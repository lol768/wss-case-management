# Wellbeing app

## Setup for devs

* Create a PostgreSQL database and user
* Copy application-example.conf to application.conf
  * Set `domain`
  * Update DB details
  * Update your `sso-client` settings
* `npm install`
* `npm run dev`
* `./sbt "run 8080"`