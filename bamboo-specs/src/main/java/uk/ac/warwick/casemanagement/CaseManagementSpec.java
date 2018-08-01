package uk.ac.warwick.casemanagement;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.deployment.Deployment;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.builders.notification.DeploymentStartedAndFinishedNotification;
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

  // Matches by name as found in Bamboo
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
        .activatorWithAssets("sbt")
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
