package cn.youhuale.leitu.capability.access.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 声明式 RBAC-lite 映射：主体→角色、动作→所需角色。
 *
 * <p>纯数据、构建后不可变、进程内查表 O(1)——"谁能做什么"一张表即全部真相。
 * 未配置的动作/主体一律不放行（deny-by-default 在 Guard 侧执行）。
 *
 * <p>用法：
 * <pre>{@code
 * RoleMap map = RoleMap.create()
 *         .subject("alice").hasRoles("admin")
 *         .subject("bob").hasRoles("member")
 *         .action("order:cancel").allowsRoles("admin")
 *         .action("order:view").allowsRoles("admin", "member")
 *         .build();
 * }</pre>
 */
public final class RoleMap {

    private final Map<String, Set<String>> subjectRoles;
    private final Map<String, Set<String>> actionRoles;

    private RoleMap(Map<String, Set<String>> subjectRoles, Map<String, Set<String>> actionRoles) {
        this.subjectRoles = subjectRoles;
        this.actionRoles = actionRoles;
    }

    public static Builder create() {
        return new Builder();
    }

    /** 主体拥有的角色（未配置返回空集）。 */
    public Set<String> rolesOfSubject(String subject) {
        return subjectRoles.getOrDefault(subject, Set.of());
    }

    /** 动作要求的角色（未配置返回空集=默认不放行）。 */
    public Set<String> rolesRequiredBy(String action) {
        return actionRoles.getOrDefault(action, Set.of());
    }

    /** 主体角色与动作要求角色有交集才放行。 */
    public boolean permits(String action, String subject) {
        Set<String> required = actionRoles.get(action);
        if (required == null || required.isEmpty()) {
            return false;
        }
        Set<String> owned = subjectRoles.getOrDefault(subject, Set.of());
        for (String role : owned) {
            if (required.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public static final class Builder {

        private final Map<String, Set<String>> subjectRoles = new LinkedHashMap<>();
        private final Map<String, Set<String>> actionRoles = new LinkedHashMap<>();

        public SubjectStage subject(String subject) {
            return new SubjectStage(this, subject);
        }

        public ActionStage action(String action) {
            return new ActionStage(this, action);
        }

        public RoleMap build() {
            return new RoleMap(Map.copyOf(subjectRoles), Map.copyOf(actionRoles));
        }

        Builder addSubjectRoles(String subject, List<String> roles) {
            subjectRoles.computeIfAbsent(subject, k -> new TreeSet<>()).addAll(roles);
            return this;
        }

        Builder addActionRoles(String action, List<String> roles) {
            actionRoles.computeIfAbsent(action, k -> new TreeSet<>()).addAll(roles);
            return this;
        }
    }

    public static final class SubjectStage {

        private final Builder builder;
        private final String subject;

        private SubjectStage(Builder builder, String subject) {
            this.builder = builder;
            this.subject = subject;
        }

        public Builder hasRoles(String... roles) {
            return builder.addSubjectRoles(subject, List.of(roles));
        }
    }

    public static final class ActionStage {

        private final Builder builder;
        private final String action;

        private ActionStage(Builder builder, String action) {
            this.builder = builder;
            this.action = action;
        }

        public Builder allowsRoles(String... roles) {
            return builder.addActionRoles(action, List.of(roles));
        }
    }
}
