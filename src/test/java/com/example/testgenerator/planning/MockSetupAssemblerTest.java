package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.DtoAnalyzer;
import com.example.testgenerator.analysis.model.*;
import com.example.testgenerator.planning.model.MockAction;
import com.example.testgenerator.planning.model.MockSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.testgenerator.planning.Fixtures.*;

class MockSetupAssemblerTest {

    private final MockSetupAssembler assembler = new MockSetupAssembler(new DefaultValueResolver(new DtoAnalyzer()));

    @Test
    void forDependencyCall_returns_stub_and_verify_when_result_is_assigned() {

        // given
        MethodCallModel call = call("repository", "save", CallKind.DEPENDENCY, "entity");

        MethodModel method = method(
                "send",
                "Notification",
                List.of(),
                List.of(call),
                List.of(assign("saved", "Notification", "repository.save(entity)")),
                List.of(ret("saved")),
                List.of(),
                List.of()
        );

        // when
        List<MockSetup> setups = assembler.forDependencyCall(call, method);

        // then
        assertThat(setups)
                .hasSize(2)
                .extracting(MockSetup::action)
                .containsExactly(MockAction.RETURN, MockAction.VERIFY);
        assertThat(setups.getFirst().value()).isEqualTo("saved");
    }

    @Test
    void forDependencyCall_returns_only_verify_when_result_is_unused() {

        // given — a side-effect call with no assignment and no return
        MethodCallModel call = call("emailService", "sendWelcomeEmail", CallKind.DEPENDENCY, "email");

        MethodModel method = method(
                "create",
                "User",
                List.of(),
                List.of(call),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        // when
        List<MockSetup> setups = assembler.forDependencyCall(call, method);

        // then
        assertThat(setups)
                .hasSize(1);
        assertThat(setups.getFirst().action()).isEqualTo(MockAction.VERIFY);
    }

    @Test
    void forDependencyCall_wraps_in_optional_when_orElseThrow_is_used() {

        // given — findById(id).orElseThrow()
        MethodCallModel call = call("repository", "findById", CallKind.DEPENDENCY, "id");

        MethodModel method = method(
                "findById",
                "User",
                List.of(),
                List.of(call),
                List.of(),
                List.of(ret("repository.findById(id).orElseThrow()")),
                List.of(),
                List.of()
        );

        // when
        List<MockSetup> setups = assembler.forDependencyCall(call, method);

        // then
        assertThat(setups)
                .hasSize(2);
        MockSetup returnSetup = setups.getFirst();
        assertThat(returnSetup.action()).isEqualTo(MockAction.RETURN);
        assertThat(returnSetup.value()).isEqualTo("java.util.Optional.of(expectedUser)");
    }

    @Test
    void forResultGetters_stubs_boolean_getters_on_result_locals() {

        // given — Notification receipt = deliveryClient.deliver(...);
        //         if (!receipt.isAccepted()) { ... }
        MethodCallModel deliver = call("deliveryClient", "deliver", CallKind.DEPENDENCY, "recipient", "body");
        MethodCallModel isAccepted = call("receipt", "isAccepted", CallKind.LOCAL);

        MethodModel method = method(
                "send",
                "Notification",
                List.of(),
                List.of(deliver, isAccepted),
                List.of(assign("receipt", "DeliveryReceipt",
                        "deliveryClient.deliver(recipient, body)")),
                List.of(),
                List.of(),
                List.of()
        );

        // when
        List<MockSetup> setups = assembler.forResultGetters(method, java.util.Set.of());

        // then
        assertThat(setups)
                .hasSize(1);
        assertThat(setups.getFirst().dependency()).isEqualTo("receipt");
        assertThat(setups.getFirst().method()).isEqualTo("isAccepted");
        assertThat(setups.getFirst().value()).isEqualTo("true");
    }

    @Test
    void forResultGetters_skips_non_boolean_getters_inside_if_body() {

        // given — receipt.getMessageId() only appears inside the if-body
        MethodCallModel deliver = call("deliveryClient", "deliver", CallKind.DEPENDENCY);
        StatementContext insideIf = new StatementContext(
                List.of("!receipt.isAccepted()"),
                0, 0, false, "", StatementContext.BranchPosition.NONE
        );
        MethodCallModel getMessageId = call(
                "receipt", "getMessageId", CallKind.LOCAL, insideIf
        );

        MethodModel method = method(
                "send",
                "Notification",
                List.of(),
                List.of(deliver, getMessageId),
                List.of(assign("receipt", "DeliveryReceipt",
                        "deliveryClient.deliver()")),
                List.of(),
                List.of(),
                List.of()
        );

        // when
        List<MockSetup> setups = assembler.forResultGetters(method, java.util.Set.of());

        // then — no stub for getMessageId
        assertThat(setups)
                .noneMatch(s -> "getMessageId".equals(s.method()));
    }
}