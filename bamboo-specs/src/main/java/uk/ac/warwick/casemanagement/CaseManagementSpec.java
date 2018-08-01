package uk.ac.warwick.casemanagement;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.deployment.Deployment;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.requirement.Requirement;
import com.atlassian.bamboo.specs.builders.notification.DeploymentStartedAndFinishedNotification;
import com.atlassian.bamboo.specs.builders.task.*;
import com.atlassian.bamboo.specs.model.task.ScriptTaskProperties;
import com.atlassian.bamboo.specs.model.task.TestParserTaskProperties;
import uk.ac.warwick.bamboo.specs.AbstractWarwickBuildSpec;

import java.util.Collection;
import java.util.Collections;

/**
 * Plan configuration for Bamboo.
 * Learn more on: <a href="https://confluence.atlassian.com/display/BAMBOO/Bamboo+Specs">https://confluence.atlassian.com/display/BAMBOO/Bamboo+Specs</a>
 */
@BambooSpec
public class CaseManagementSpec extends AbstractWarwickBuildSpec {

  private static final Project PROJECT =
    new Project()
      .key("CASE")
      .name("Case Management");

  private static final String LINKED_REPOSITORY = "Case Management";

  private static final String SLACK_CHANNEL = "#case-management";

  public static void main(String[] args) throws Exception {
    new CaseManagementSpec().publish();
  }

  @Override
  protected Collection<Plan> builds() {
    return Collections.singleton(
      build(PROJECT, "APP", "Case Management")
        .linkedRepository(LINKED_REPOSITORY)
        .description("Build application")
        .stage(
          new Stage("Build stage")
            .jobs(
              new Job("Build and check", "BUILD")
                .tasks(
                  new VcsCheckoutTask()
                    .description("Checkout source from default repository")
                    .checkoutItems(new CheckoutItem().defaultRepository()),
                  new NpmTask()
                    .description("prune")
                    .nodeExecutable("Node 8")
                    .command("prune"),
                  new NpmTask()
                    .description("dependencies")
                    .nodeExecutable("Node 8")
                    .command("install -d"),
                  new ScriptTask()
                    .description("Run tests and package")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .location(ScriptTaskProperties.Location.FILE)
                    .fileFromPath("sbt")
                    .argument("clean test:compile test universal:packageZipTarball")
                    .environmentVariables("PATH=/usr/nodejs/8/bin")
                )
                .finalTasks(
                  new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                    .description("Parse test results")
                    .resultDirectories("**/test-reports/*.xml"),
                  TestParserTask.createMochaParserTask()
                    .defaultResultDirectory()
                )
                .artifacts(
                  new Artifact()
                    .name("tar.gz")
                    .copyPattern("app.tar.gz")
                    .location("target/universal")
                    .shared(true)
                )
                .requirements(
                  new Requirement("Linux").matchValue("true").matchType(Requirement.MatchType.EQUALS)
                )
            )
        )
        .slackNotifications(SLACK_CHANNEL, false)
        .build()
    );
  }

  @Override
  protected Collection<Deployment> deployments() {
    return Collections.singleton(
      deployment(PROJECT, "APP", "Case Management")
        .autoPlayEnvironment("Development", "wellbeing-dev.warwick.ac.uk", "wellbeing", "dev", SLACK_CHANNEL)
        .autoPlayEnvironment("Test", "wellbeing-test.warwick.ac.uk", "wellbeing", "test", SLACK_CHANNEL)
        .playEnvironment("Production", "wellbeing.warwick.ac.uk", "wellbeing", "prod",
          env -> env.notifications(
            new Notification()
              .type(new DeploymentStartedAndFinishedNotification())
              .recipients(slackRecipient(SLACK_CHANNEL))
          )
        )
        .build()
    );
  }

}
