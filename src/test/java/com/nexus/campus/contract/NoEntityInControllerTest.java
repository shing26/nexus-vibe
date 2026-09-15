package com.nexus.campus.contract;

import com.nexus.campus.dto.ChannelVo;
import com.nexus.campus.dto.CommentVo;
import com.nexus.campus.dto.MessageVo;
import com.nexus.campus.dto.ProfileVo;
import com.nexus.campus.dto.TagVo;
import com.nexus.campus.entity.Channel;
import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibeTag;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two halves of the same boundary, both checked structurally rather than by
 * review:
 *
 * <ol>
 *   <li>No HTTP handler signature mentions a persistence entity. The check asks
 *       the real {@link RequestMappingHandlerMapping} what it registered, so it
 *       cannot be satisfied by a controller that merely looks tidy. Entities may
 *       still appear in a controller body as local variables - that is how a VO
 *       gets built - so imports and locals are deliberately not the criterion.</li>
 *   <li>The VOs are the entities minus {@code SysUser.password}, field for
 *       field. They are a boundary, not a redesign: nothing here renames or
 *       drops a value a client already reads, and a future column cannot ride
 *       out into the open just because somebody added it to the table.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/data.sql", "/test-users.sql"})
class NoEntityInControllerTest {

    private static final String ENTITY_PACKAGE = "com.nexus.campus.entity";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtUtil.generateToken(2L, "testuser", "USER");
    }

    @Test
    @DisplayName("Every registered handler signature is free of persistence entities")
    void handlerSignaturesMentionNoEntity() {
        List<String> offenders = new ArrayList<>();

        for (HandlerMethod handler : handlerMapping.getHandlerMethods().values()) {
            for (Type t : entityTypesIn(handler.getMethod().getGenericReturnType())) {
                offenders.add(handler + " returns " + t);
            }
            for (MethodParameter parameter : handler.getMethodParameters()) {
                for (Type t : entityTypesIn(parameter.getParameterType())) {
                    offenders.add(handler + " takes " + t);
                }
            }
        }

        assertThat(offenders)
                .as("entities stop at the service layer; map to a VO in the controller")
                .isEmpty();
    }

    @Test
    @DisplayName("Self-profile responses carry no password key, on either route that serves it")
    void profileResponsesCarryNoPassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nickname\":\"Boundary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("Each VO is its entity minus the password hash, field for field")
    void vosMirrorTheirEntities() {
        assertThat(persistentFieldsOf(ProfileVo.class)).isEqualTo(entityFieldsOf(SysUser.class, "password"));
        assertThat(persistentFieldsOf(CommentVo.class)).isEqualTo(entityFieldsOf(VibeComment.class));
        assertThat(persistentFieldsOf(MessageVo.class)).isEqualTo(entityFieldsOf(SysMessage.class));
        assertThat(persistentFieldsOf(TagVo.class)).isEqualTo(entityFieldsOf(VibeTag.class));
        assertThat(persistentFieldsOf(ChannelVo.class)).isEqualTo(entityFieldsOf(Channel.class));
    }

    /** Declared instance fields of a VO, i.e. what a client can be handed. */
    private static Set<String> persistentFieldsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !f.isSynthetic() && !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Declared instance fields of an entity, minus any names listed as excluded. */
    private static Set<String> entityFieldsOf(Class<?> type, String... excluded) {
        Set<String> skip = new LinkedHashSet<>(Arrays.asList(excluded));
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !f.isSynthetic() && !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .filter(name -> !skip.contains(name))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Type> entityTypesIn(Type type) {
        List<Type> found = new ArrayList<>();
        collectEntityTypes(type, found);
        return found;
    }

    private static void collectEntityTypes(Type type, List<Type> found) {
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.getName().startsWith(ENTITY_PACKAGE + ".")) {
                found.add(clazz);
            }
            if (clazz.isArray()) {
                collectEntityTypes(clazz.getComponentType(), found);
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            collectEntityTypes(parameterized.getRawType(), found);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectEntityTypes(argument, found);
            }
        }
    }
}
