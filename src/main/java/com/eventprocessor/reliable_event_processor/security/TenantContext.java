package com.eventprocessor.reliable_event_processor.security;

import com.eventprocessor.reliable_event_processor.service.TenantService;

/**
 * ThreadLocal container for propagating tenant identity within request and background processing threads.
 * Must always be cleared after request or worker execution to prevent thread pool leakage.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PLATFORM_ADMIN_BYPASS = new ThreadLocal<>();

    private TenantContext() {
    }

    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantService.DEFAULT_TENANT_ID;
    }

    public static String getExplicitTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            CURRENT_TENANT.set(tenantId.trim());
        } else {
            CURRENT_TENANT.remove();
        }
    }

    public static boolean isPlatformAdminBypass() {
        Boolean bypass = PLATFORM_ADMIN_BYPASS.get();
        return bypass != null && bypass;
    }

    public static void setPlatformAdminBypass(boolean bypass) {
        if (bypass) {
            PLATFORM_ADMIN_BYPASS.set(true);
        } else {
            PLATFORM_ADMIN_BYPASS.remove();
        }
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        PLATFORM_ADMIN_BYPASS.remove();
    }
}
