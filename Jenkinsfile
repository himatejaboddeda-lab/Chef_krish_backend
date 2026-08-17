// Jenkinsfile — Chef Krish continuous verification pipeline.
//
// This does NOT build or deploy anything. index.html is served directly
// from GitHub Pages and worker.js is deployed via Cloudflare Quick Edit —
// both deploy independently of Jenkins. This pipeline's only job is to
// run the QA suite in src/test/java against whatever is LIVE right now,
// on every push and on a schedule, and fail loudly if either the API or
// the frontend has regressed.
//
// Required Jenkins setup (one-time, see docs/JENKINS_SETUP.md):
//   1. A Jenkins agent with JDK 17 + Maven 3.9+ + Google Chrome installed
//      (or use the Docker agent block below instead — see comment).
//   2. A "Secret text" credential named CHEFKRISH_ADMIN_KEY holding the
//      same value as the Cloudflare Worker's ADMIN_KEY secret. Optional —
//      admin-route tests SKIP (not fail) if this isn't configured.
//   3. A GitHub webhook (Settings → Webhooks → payload URL
//      <jenkinsUrl>/github-webhook/) pointed at this repo, OR rely on the
//      pollSCM trigger below if a webhook isn't available.

pipeline {
    agent any

    // Jenkins manages this installation itself (Manage Jenkins → Tools →
    // Maven installations → Add Maven, name it exactly "Maven3", check
    // "Install automatically"). This puts Maven's bin/ on PATH for every
    // stage below regardless of what's installed on the host machine —
    // the pipeline no longer depends on the Jenkins process's own PATH,
    // which is what caused "mvn: command not found" (exit 127) before.
    tools {
        maven 'Maven3'
        jdk 'Java17'
    }

    options {
        timestamps()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
        disableConcurrentBuilds()
    }

    triggers {
        // Fires on a GitHub push webhook, if configured (preferred).
        githubPush()
        // Fallback safety net: also re-verify every 15 minutes even with
        // no push, since this pipeline tests LIVE endpoints that can
        // regress independently of a git push (a bad Cloudflare Quick
        // Edit deploy, a Neo4j data change, an expired API key, etc.).
        pollSCM('H/15 * * * *')
    }

    environment {
        // Overridable at Jenkins job level (Configure → Build Environment
        // → Parameters) without touching this file, e.g. to point a
        // manually-triggered build at a staging Worker instead.
        CHEFKRISH_BASE_URL     = "${params.CHEFKRISH_BASE_URL ?: 'https://chef-krish-backend.boddedahimateja.workers.dev'}"
        CHEFKRISH_FRONTEND_URL = "${params.CHEFKRISH_FRONTEND_URL ?: 'https://himatejaboddeda-lab.github.io/Chef_krish_backend/'}"
    }

    parameters {
        string(name: 'CHEFKRISH_BASE_URL', defaultValue: '', description: 'Override the Worker API base URL (blank = production)')
        string(name: 'CHEFKRISH_FRONTEND_URL', defaultValue: '', description: 'Override the GitHub Pages frontend URL (blank = production)')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('API tests (fast fail-fast)') {
            // Runs first and alone: no browser needed, ~seconds not
            // minutes. If the Worker API itself is broken, there is no
            // point spending time booting Chrome for the UI stage below.
            steps {
                sh """
                    mvn -B -ntp clean test -P api-only \
                        -Dchefkrish.baseUrl=${CHEFKRISH_BASE_URL} \
                        -Dchefkrish.frontendUrl=${CHEFKRISH_FRONTEND_URL}
                """
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Admin-route tests (secret-gated)') {
            // Re-run just the admin suite WITH the real key injected.
            // withCredentials scopes ADMIN_KEY to this one block only —
            // it is never exported for the rest of the pipeline. If the
            // CHEFKRISH_ADMIN_KEY credential hasn't been configured yet,
            // this stage is marked UNSTABLE (not failed) rather than
            // blocking the whole build — admin coverage is a bonus on
            // top of the API/UI stages, not a hard requirement.
            steps {
                script {
                    try {
                        withCredentials([string(credentialsId: 'CHEFKRISH_ADMIN_KEY', variable: 'ADMIN_KEY')]) {
                            sh """
                                mvn -B -ntp test -Dtest=AdminEndpointTest \
                                    -Dchefkrish.baseUrl=${CHEFKRISH_BASE_URL} \
                                    -Dchefkrish.adminKey=${ADMIN_KEY}
                            """
                        }
                    } catch (err) {
                        echo "CHEFKRISH_ADMIN_KEY credential not configured (or admin tests failed) — marking build UNSTABLE, not failed. Details: ${err}"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('UI smoke test (Selenium, headless Chrome)') {
            steps {
                sh """
                    mvn -B -ntp test -Dtest=ChefKrishUiSmokeTest \
                        -Dchefkrish.frontendUrl=${CHEFKRISH_FRONTEND_URL}
                """
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }
    }

    post {
        failure {
            echo "❌ Chef Krish verification failed on ${env.BUILD_URL} — investigate before this reaches a customer."
            // Wire up your notification channel of choice here, e.g.:
            //   slackSend(channel: '#chef-krish-alerts', color: 'danger',
            //             message: "Chef Krish CI failed: ${env.BUILD_URL}")
            //   mail to: 'you@example.com', subject: "Chef Krish CI failed",
            //        body: "See ${env.BUILD_URL}"
        }
        always {
            archiveArtifacts artifacts: 'target/surefire-reports/**', allowEmptyArchive: true
            cleanWs()
        }
    }
}

// Note: Chrome must be installed on whichever agent runs this pipeline for
// the Selenium stage to work. If your Jenkins agent doesn't have it, swap
// `agent any` at the top for a Docker agent instead, e.g.:
//
//   agent {
//       docker {
//           image 'maven:3.9-eclipse-temurin-17'
//           args '-v /var/run/docker.sock:/var/run/docker.sock'
//       }
//   }
//
// and run Selenium against a separate selenium/standalone-chrome
// container, pointing WebDriver at its remote URL instead of a local
// ChromeDriver — see docs/JENKINS_SETUP.md for the docker-compose variant.
