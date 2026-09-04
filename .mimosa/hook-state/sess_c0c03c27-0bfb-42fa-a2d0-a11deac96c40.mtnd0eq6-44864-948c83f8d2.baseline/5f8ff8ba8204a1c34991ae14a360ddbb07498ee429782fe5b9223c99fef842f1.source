/**
 * Nexus Campus - Form validation module
 * Client-side validation utilities for forms
 */
(function() {
    "use strict";

    /**
     * Validate that a field is not empty.
     * @param {string} fieldId - DOM element ID
     * @param {string} label - Human-readable field name
     * @returns {boolean} true if valid
     */
    window.validateRequired = function(fieldId, label) {
        var field = document.getElementById(fieldId);
        if (!field) return true;
        var val = field.value.trim();
        if (!val) {
            showFieldError(fieldId, label + " is required.");
            return false;
        }
        clearFieldError(fieldId);
        return true;
    };

    /**
     * Validate email format.
     * @param {string} fieldId - DOM element ID
     * @returns {boolean} true if valid or empty
     */
    window.validateEmail = function(fieldId) {
        var field = document.getElementById(fieldId);
        if (!field) return true;
        var val = field.value.trim();
        if (!val) return true; // optional field
        var re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!re.test(val)) {
            showFieldError(fieldId, "Please enter a valid email address.");
            return false;
        }
        clearFieldError(fieldId);
        return true;
    };

    /**
     * Validate password strength (min 6 chars, at least 1 letter and 1 digit).
     * @param {string} fieldId - DOM element ID
     * @returns {boolean} true if valid
     */
    window.validatePassword = function(fieldId) {
        var field = document.getElementById(fieldId);
        if (!field) return true;
        var val = field.value;
        if (val.length < 6) {
            showFieldError(fieldId, "Password must be at least 6 characters.");
            return false;
        }
        if (!/[a-zA-Z]/.test(val) || !/[0-9]/.test(val)) {
            showFieldError(fieldId, "Password must contain at least one letter and one digit.");
            return false;
        }
        clearFieldError(fieldId);
        return true;
    };

    /**
     * Validate minimum field length.
     * @param {string} fieldId - DOM element ID
     * @param {number} min - Minimum length
     * @returns {boolean} true if valid
     */
    window.validateMinLength = function(fieldId, min) {
        var field = document.getElementById(fieldId);
        if (!field) return true;
        var val = field.value.trim();
        if (val.length < min) {
            showFieldError(fieldId, "Must be at least " + min + " characters.");
            return false;
        }
        clearFieldError(fieldId);
        return true;
    };

    /**
     * Show a field-level error message and highlight the field.
     * @param {string} fieldId - DOM element ID of the field
     * @param {string} message - Error message text
     */
    window.showFieldError = function(fieldId, message) {
        var field = document.getElementById(fieldId);
        if (field) {
            field.classList.add("input-error");
            field.classList.remove("input-success");
        }
        var feedback = document.getElementById(fieldId + "-error");
        if (feedback) {
            feedback.textContent = message;
            feedback.className = "field-feedback field-error";
        }
    };

    /**
     * Clear field-level error and reset styling.
     * @param {string} fieldId - DOM element ID of the field
     */
    window.clearFieldError = function(fieldId) {
        var field = document.getElementById(fieldId);
        if (field) {
            field.classList.remove("input-error");
            field.classList.add("input-success");
        }
        var feedback = document.getElementById(fieldId + "-error");
        if (feedback) {
            feedback.textContent = "";
            feedback.className = "field-feedback";
        }
    };

})();