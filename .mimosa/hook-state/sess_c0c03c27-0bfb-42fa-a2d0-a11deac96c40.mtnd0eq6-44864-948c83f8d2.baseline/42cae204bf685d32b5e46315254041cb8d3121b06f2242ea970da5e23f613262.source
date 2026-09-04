 package com.nexus.campus.event;
 
 import lombok.Getter;
 import org.springframework.context.ApplicationEvent;
 
 @Getter
 public class MessageEvent extends ApplicationEvent {
 
     private final Long senderId;
     private final Long receiverId;
     private final String msgType;   // "like", "comment", "system"
     private final String content;
     private final Long targetId;    // postId or commentId
 
     public MessageEvent(Object source, Long senderId, Long receiverId,
                         String msgType, String content, Long targetId) {
         super(source);
         this.senderId = senderId;
         this.receiverId = receiverId;
         this.msgType = msgType;
         this.content = content;
         this.targetId = targetId;
     }
 }
