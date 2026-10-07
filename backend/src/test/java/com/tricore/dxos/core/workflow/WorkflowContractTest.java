package com.tricore.dxos.core.workflow;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static com.tricore.dxos.core.workflow.WorkflowInstanceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowContractTest {
    @Test
    void commandCannotSupplyTargetStateLifecycleOrNewVersion() {
        assertThat(Arrays.stream(TransitionCommand.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("instanceId", "transitionId", "actorReference", "expectedVersion");
        assertThat(WorkflowInstance.class.getConstructors()).isEmpty();
        assertThat(TransitionResult.class.getConstructors()).isEmpty();
        assertThat(Arrays.stream(WorkflowInstance.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers())))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()));
        assertThat(Arrays.stream(WorkflowInstance.class.getMethods()).map(method -> method.getName()))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void rejectsIncompleteCommandsAndReferences() {
        assertThatThrownBy(() -> new TransitionCommand("", "submit", ACTOR, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransitionCommand("i", " ", ACTOR, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransitionCommand("i", "submit", null, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransitionCommand("i", "submit", ACTOR, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActorReference(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResourceReference(null, "id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResourceReference("type", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsHistoryThatDisagreesWithTransitionSnapshots() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        TransitionResult valid = active(definition).transition(definition, command("submit", 0), NOW);
        List<TransitionRecord> inconsistent = List.of(
                new TransitionRecord("another", "submit", "draft", "review", ACTOR, 1, NOW),
                new TransitionRecord("instance-1", "submit", "review", "review", ACTOR, 1, NOW),
                new TransitionRecord("instance-1", "submit", "draft", "published", ACTOR, 1, NOW),
                new TransitionRecord("instance-1", "submit", "draft", "review", ACTOR, 2, NOW),
                new TransitionRecord("instance-1", "submit", "draft", "review", ACTOR, 1, NOW.plusSeconds(1)));
        for (TransitionRecord record : inconsistent) {
            assertThatThrownBy(() -> new TransitionResult(valid.previousInstance(), valid.instance(), record))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new TransitionResult(valid.previousInstance(), valid.previousInstance(), valid.record()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historyRequiresPositiveResultVersionAndTrustedActorReference() {
        assertThatThrownBy(() -> new TransitionRecord("i", "t", "a", "b", ACTOR, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransitionRecord("i", "t", "a", "b", null, 1, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransitionRecord("i", "t", "a", "b", ACTOR, 1, null))
                .isInstanceOf(NullPointerException.class);
    }
}
