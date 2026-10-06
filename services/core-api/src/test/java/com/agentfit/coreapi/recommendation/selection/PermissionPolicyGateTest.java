package com.agentfit.coreapi.recommendation.selection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionPolicyGateTest {
    @Test
    void supportedActionDefaultsToAskAndNeverToAllow() {
        PermissionMapping mapping = new PermissionMapping("tool", "external.connect", true,
            Set.of(PermissionPolicy.ASK_EACH_TIME, PermissionPolicy.ALWAYS_ALLOW));
        assertEquals(List.of(new PermissionSelection("tool", "external.connect", PermissionPolicy.ASK_EACH_TIME)),
            PermissionPolicyGate.defaults(List.of(mapping)));
    }

    @Test
    void unsupportedAskCannotBeDowngradedOrUpgraded() {
        PermissionMapping mapping = new PermissionMapping("tool", "external.connect", true,
            Set.of(PermissionPolicy.ALWAYS_ALLOW));
        assertThrows(PermissionPolicyGate.BlockedSelectionException.class,
            () -> PermissionPolicyGate.defaults(List.of(mapping)));
        assertThrows(PermissionPolicyGate.BlockedSelectionException.class,
            () -> PermissionPolicyGate.validate(List.of(mapping),
                List.of(new PermissionSelection("tool", "external.connect", PermissionPolicy.ASK_EACH_TIME))));
    }

    @Test
    void deniedRequiredActionAndMissingOrDuplicateMappingsBlockConfiguration() {
        PermissionMapping required = new PermissionMapping("tool", "filesystem.write", true,
            Set.of(PermissionPolicy.DENY, PermissionPolicy.ASK_EACH_TIME));
        PermissionSelection denied = new PermissionSelection("tool", "filesystem.write", PermissionPolicy.DENY);
        assertThrows(PermissionPolicyGate.BlockedSelectionException.class,
            () -> PermissionPolicyGate.validate(List.of(required), List.of(denied)));
        assertThrows(PermissionPolicyGate.BlockedSelectionException.class,
            () -> PermissionPolicyGate.validate(List.of(required), List.of()));
        PermissionSelection asked = new PermissionSelection("tool", "filesystem.write", PermissionPolicy.ASK_EACH_TIME);
        assertThrows(PermissionPolicyGate.BlockedSelectionException.class,
            () -> PermissionPolicyGate.validate(List.of(required), List.of(asked, asked)));
    }

    @Test
    void validExplicitChoicePassesOnlyTheReviewedMapping() {
        PermissionMapping optional = new PermissionMapping("tool", "external.connect", false,
            Set.of(PermissionPolicy.DENY, PermissionPolicy.ASK_EACH_TIME));
        PermissionSelection denied = new PermissionSelection("tool", "external.connect", PermissionPolicy.DENY);
        assertEquals(List.of(denied), PermissionPolicyGate.validate(List.of(optional), List.of(denied)));
        assertThrows(PermissionPolicyGate.BlockedSelectionException.class,
            () -> PermissionPolicyGate.validate(List.of(optional),
                List.of(new PermissionSelection("other", "external.connect", PermissionPolicy.DENY))));
    }
}
