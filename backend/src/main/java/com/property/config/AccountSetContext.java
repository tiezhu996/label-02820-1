package com.property.config;

public class AccountSetContext {
    private static final ThreadLocal<Long> ACCOUNT_SET_ID = new ThreadLocal<>();
    
    public static void setCurrentAccountSetId(Long accountSetId) {
        ACCOUNT_SET_ID.set(accountSetId);
    }
    
    public static Long getCurrentAccountSetId() {
        Long id = ACCOUNT_SET_ID.get();
        return id != null ? id : 1L;
    }
    
    public static void clear() {
        ACCOUNT_SET_ID.remove();
    }
}
