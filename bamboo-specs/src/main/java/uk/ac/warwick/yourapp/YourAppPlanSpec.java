package uk.ac.warwick.yourapp;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.deployment.Deployment;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.builders.notification.DeploymentStartedAndFinishedNotification;
import com.atlassian.bamboo.specs.builders.trigger.ScheduledTrigger;
import uk.ac.warwick.bamboo.specs.AbstractWarwickBuildSpec;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Plan configuration for Bamboo.
 * Learn more on: <a href="https://confluence.atlassian.com/display/BAMBOO/Bamboo+Specs">https://confluence.atlassian.com/display/BAMBOO/Bamboo+Specs</a>
 */
@BambooSpec
public class YourAppPlanSpec extends AbstractWarwickBuildSpec {

  private static final Project PROJECT =
    new Project()
      .key("YOURAPP")
      .name("APP NAME")
      .description("...");

  private static final String LINKED_REPOSITORY = "YOUR_APP";

  private static final String SLACK_CHANNEL = "#something";

  public static void main(String[] args) throws Exception {
    new YourAppPlanSpec().publish();
  }

  @Override
  protected Collection<Plan> builds() {
    return Collections.singleton(
      build(PROJECT, "APP", "YOUR_APP")
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
      deployment(PROJECT, "APP", "YOUR_APP")
        .autoPlayEnvironment("Development", "changeme-dev.warwick.ac.uk", "changeme", "dev", SLACK_CHANNEL)
        .autoPlayEnvironment("Test", "changeme-test.warwick.ac.uk", "changeme", "test", SLACK_CHANNEL)
        .playEnvironment("Production", "changeme.warwick.ac.uk", "changeme", "prod",
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
