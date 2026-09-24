package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.*;
import com.example.testgenerator.planning.model.TestData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.testgenerator.planning.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class TestDataAssemblerTest {

    private final TestDataAssembler assembler = testDataAssembler();

    // ---------- parameters ----------

    @Test
    void emits_scalar_parameter_with_default_literal() {

        // given
        MethodModel method = method(
                "countByStatus",
                "long",
                List.of(new ParameterModel("String", "status")),
                List.of()
        );

        // when
        List<TestData> data = assembler.assemble(method, null);

        // then
        assertThat(data).hasSize(1);
        assertThat(data.get(0).variableName()).isEqualTo("status");
        assertThat(data.get(0).type()).isEqualTo("String");
        assertThat(data.get(0).initialization()).isEqualTo("\"test@example.com\"");
    }

    @Test
    void emits_null_for_parameter_constrained_by_guard() {

        // given — if (status == null || status.isBlank()) throw ...
        ConditionModel guard = new ConditionModel(
                "status == null || status.isBlank()",
                List.of(),
                List.of("IllegalArgumentException"),
                StatementContext.topLevel()
        );
        MethodModel method = methodWithConditions(
                "countByStatus",
                "long",
                List.of(new ParameterModel("String", "status")),
                List.of(guard)
        );

        // when
        List<TestData> data = assembler.assemble(method, guard);

        // then — null triggers the guard
        assertThat(data).hasSize(1);
        assertThat(data.get(0).initialization()).isEqualTo("null");
    }

    @Test
    void emits_empty_string_for_blank_only_guard() {

        // given — if (status.isBlank()) throw ...   (no null check)
        ConditionModel guard = new ConditionModel(
                "status.isBlank()",
                List.of(),
                List.of("IllegalArgumentException"),
                StatementContext.topLevel()
        );
        MethodModel method = methodWithConditions(
                "countByStatus",
                "long",
                List.of(new ParameterModel("String", "status")),
                List.of(guard)
        );

        // when
        List<TestData> data = assembler.assemble(method, guard);

        // then — "" triggers isBlank() and doesn't need to also satisfy a null check
        assertThat(data).hasSize(1);
        assertThat(data.get(0).initialization()).isEqualTo("\"\"");
    }

    // ---------- assignments ----------

    @Test
    void emits_mock_for_locals_bound_to_dependency_call() {

        // given — Notification saved = repository.save(entity);
        MethodModel method = method(
                "send",
                "Notification",
                List.of(),
                List.of(call("repository", "save", CallKind.DEPENDENCY, "entity")),
                List.of(assign("saved", "Notification", "repository.save(entity)")),
                List.of(ret("saved")),
                List.of()
        );

        // when
        List<TestData> data = assembler.assemble(method, null);

        // then — assignment appears with mock initializer
        assertThat(data)
                .anyMatch(td ->
                        td.variableName().equals("saved")
                        && td.type().equals("Notification")
                        && td.initialization().equals("mock(Notification.class)"));
    }

    @Test
    void emits_non_empty_list_when_collection_is_iterated() {

        // given — List<Notification> pending = ...; for(...) pending.get(i)
        MethodCallModel findPending = call("repository", "findPendingByChannel", CallKind.DEPENDENCY, "channel");
        MethodCallModel get = call("pending", "get", CallKind.LOCAL, "i");

        MethodModel method = method(
                "retryPending",
                "void",
                List.of(),
                List.of(findPending, get),
                List.of(
                        assign("pending", "List<Notification>", "repository.findPendingByChannel(channel)"),
                        assign("notification", "Notification", "pending.get(i)")
                ),
                List.of(),
                List.of()
        );

        // when
        List<TestData> data = assembler.assemble(method, null);

        // then — pending is non-empty, referencing the loop element
        TestData pending = data.stream()
                .filter(td -> td.variableName().equals("pending"))
                .findFirst()
                .orElseThrow();
        assertThat(pending.initialization()).isEqualTo("java.util.List.of(notification)");
    }

    @Test
    void emits_empty_list_when_collection_is_not_iterated() {

        // given — List<Notification> pending = ...;  (no .get / .size on it)
        MethodModel method = method(
                "getPending",
                "List<Notification>",
                List.of(),
                List.of(call("repository", "findPendingByChannel", CallKind.DEPENDENCY, "channel")),
                List.of(assign("pending", "List<Notification>", "repository.findPendingByChannel(channel)")),
                List.of(ret("pending")),
                List.of()
        );

        // when
        List<TestData> data = assembler.assemble(method, null);

        // then
        TestData pending = data.stream()
                .filter(td -> td.variableName().equals("pending"))
                .findFirst()
                .orElseThrow();
        assertThat(pending.initialization()).isEqualTo("java.util.List.of()");
    }

    // ---------- ordering ----------

    @Test
    void reorders_declarations_so_element_comes_before_collection() {

        // given — List<Notification> pending = List.of(notification);
        //         Notification notification = mock(...);
        //         source order would break compilation.
        MethodCallModel findPending = call("repository", "findPendingByChannel", CallKind.DEPENDENCY);
        MethodCallModel get = call("pending", "get", CallKind.LOCAL, "i");

        MethodModel method = method(
                "retryPending",
                "void",
                List.of(),
                List.of(findPending, get),
                List.of(
                        assign("pending", "List<Notification>", "repository.findPendingByChannel()"),
                        assign("notification", "Notification", "pending.get(i)")
                ),
                List.of(),
                List.of()
        );

        // when
        List<TestData> data = assembler.assemble(method, null);

        // then — notification before pending
        int notificationIdx = indexOf(data, "notification");
        int pendingIdx = indexOf(data, "pending");
        assertThat(notificationIdx).isLessThan(pendingIdx);
    }

    private int indexOf(List<TestData> data, String name) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).variableName().equals(name)) return i;
        }
        return -1;
    }

    // ---------- optional orElseThrow ----------

    @Test
    void emits_unwrapped_expected_variable_for_orElseThrow() {

        // given — return repository.findById(id).orElseThrow();
        MethodCallModel call = call("repository", "findById", CallKind.DEPENDENCY, "id");

        MethodModel method = method(
                "findById",
                "User",
                List.of(),
                List.of(call),
                List.of(),
                List.of(ret("repository.findById(id).orElseThrow()")),
                List.of()
        );

        // when
        List<TestData> data = assembler.assemble(method, null);

        // then — expectedUser is declared with type User (not Optional<User>)
        assertThat(data)
                .anyMatch(td ->
                        td.variableName().equals("expectedUser")
                        && td.type().equals("User"));
    }
}