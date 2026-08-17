// Jenkinsfile — Chef Krish enterprise continuous-verification pipeline.
//
// The application is deployed independently through GitHub Pages and a
// Cloudflare Worker. This pipeline tests the live application; it does not
// deploy production code.
//
// Required Jenkins tools (Manage Jenkins -> Tools):
//   Maven installation name: Maven3
//   JDK installation name:   Java17
//
// Required Jenkins Secret text credential IDs:
//   CHEFKRISH_ADMIN_KEY   Optional Cloudflare Worker admin key
//   XRAY_CLIENT_ID        Xray Cloud API client ID
//   XRAY_CLIENT_SECRET    Xray Cloud API client secret
//   N8N_WEBHOOK_URL       n8n Production URL ending /webhook/chef-krish-jenkins-results
//   N8N_WEBHOOK_SECRET    Value expected in X-Chef-Krish-Webhook-Secret
//
// JENKINS_API_TOKEN is intentionally not used here. A pipeline already running
// inside Jenkins does not need to call Jenkins through its own REST API.

def xrayUploadStatus = 'NOT_ATTEMPTED'

pipeline {
    agent any

    tools {
        maven 'Maven3'
        jdk 'Java17'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    triggers {
        githubPush()
        pollSCM('H/15 * * * *')
    }

    parameters {
        string(
            name: 'CHEFKRISH_BASE_URL',
            defaultValue: '',
            description: 'Override Worker API URL; blank uses Chef Krish production'
        )
        string(
            name: 'CHEFKRISH_FRONTEND_URL',
            defaultValue: '',
            description: 'Override frontend URL; blank uses GitHub Pages production'
        )
        string(
            name: 'XRAY_PROJECT_KEY',
            defaultValue: '',
            description: 'Jira/Xray project key, for example CKQA'
        )
        string(
            name: 'XRAY_TEST_PLAN_KEY',
            defaultValue: '',
            description: 'Optional Xray Test Plan key, for example CKQA-1'
        )
    }

    environment {
        CHEFKRISH_BASE_URL = "${params.CHEFKRISH_BASE_URL?.trim() ? params.CHEFKRISH_BASE_URL.trim() : 'https://chef-krish-backend.boddedahimateja.workers.dev'}"
        CHEFKRISH_FRONTEND_URL = "${params.CHEFKRISH_FRONTEND_URL?.trim() ? params.CHEFKRISH_FRONTEND_URL.trim() : 'https://himatejaboddeda-lab.github.io/Chef_krish_backend/'}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('API automation') {
            steps {
                // Assertion failures make this stage red and the build UNSTABLE,
                // but they do not prevent UI testing, Xray, or n8n reporting.
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        mvn -B -ntp clean test -P api-only \
                            -Dchefkrish.baseUrl=${CHEFKRISH_BASE_URL} \
                            -Dchefkrish.frontendUrl=${CHEFKRISH_FRONTEND_URL}
                    """
                }
            }
            post {
                always {
                    sh '''
                        mkdir -p target/pipeline-reports/api
                        for report in target/surefire-reports/TEST-*.xml; do
                            if [ -f "$report" ]; then
                                cp "$report" target/pipeline-reports/api/
                            fi
                        done
                    '''
                    junit testResults: 'target/pipeline-reports/api/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Admin-route automation') {
            steps {
                script {
                    sh '''
                        mkdir -p target/surefire-reports
                        find target/surefire-reports -maxdepth 1 -type f -name 'TEST-*.xml' -delete || true
                    '''

                    try {
                        withCredentials([
                            string(credentialsId: 'CHEFKRISH_ADMIN_KEY', variable: 'ADMIN_KEY')
                        ]) {
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh """
                                    mvn -B -ntp test -Dtest=AdminEndpointTest \
                                        -Dchefkrish.baseUrl=${CHEFKRISH_BASE_URL} \
                                        -Dchefkrish.adminKey=\${ADMIN_KEY}
                                """
                            }
                        }
                    } catch (err) {
                        echo 'CHEFKRISH_ADMIN_KEY is not configured. Admin tests were skipped.'
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            post {
                always {
                    sh '''
                        mkdir -p target/pipeline-reports/admin
                        for report in target/surefire-reports/TEST-*.xml; do
                            if [ -f "$report" ]; then
                                cp "$report" target/pipeline-reports/admin/
                            fi
                        done
                    '''
                    junit testResults: 'target/pipeline-reports/admin/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('UI automation - Selenium') {
            steps {
                sh '''
                    mkdir -p target/surefire-reports
                    find target/surefire-reports -maxdepth 1 -type f -name 'TEST-*.xml' -delete || true
                '''

                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        mvn -B -ntp test -Dtest=ChefKrishUiSmokeTest \
                            -Dchefkrish.frontendUrl=${CHEFKRISH_FRONTEND_URL}
                    """
                }
            }
            post {
                always {
                    sh '''
                        mkdir -p target/pipeline-reports/ui
                        for report in target/surefire-reports/TEST-*.xml; do
                            if [ -f "$report" ]; then
                                cp "$report" target/pipeline-reports/ui/
                            fi
                        done
                    '''
                    junit testResults: 'target/pipeline-reports/ui/*.xml', allowEmptyResults: true
                }
            }
        }
    }

    post {
        always {
            script {
                // Xray import is a reporting integration. If it fails, retain
                // all Jenkins results and mark the build UNSTABLE rather than
                // replacing the original test outcome with a pipeline crash.
                if (!params.XRAY_PROJECT_KEY?.trim()) {
                    xrayUploadStatus = 'SKIPPED_NO_PROJECT_KEY'
                    echo 'XRAY_PROJECT_KEY is blank. Xray upload was skipped.'
                    currentBuild.result = 'UNSTABLE'
                } else {
                    try {
                        withCredentials([
                            string(credentialsId: 'XRAY_CLIENT_ID', variable: 'XRAY_CLIENT_ID'),
                            string(credentialsId: 'XRAY_CLIENT_SECRET', variable: 'XRAY_CLIENT_SECRET')
                        ]) {
                            withEnv([
                                "XRAY_PROJECT_KEY_VALUE=${params.XRAY_PROJECT_KEY.trim()}",
                                "XRAY_TEST_PLAN_KEY_VALUE=${params.XRAY_TEST_PLAN_KEY?.trim() ?: ''}"
                            ]) {
                                sh '''
                                    set -eu
                                    set +x

                                    mkdir -p target/xray

                                    python3 <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET


def local_name(tag):
    return tag.rsplit('}', 1)[-1]


reports = glob.glob('target/pipeline-reports/**/*.xml', recursive=True)
if not reports:
    sys.exit('No Surefire XML reports were available for Xray.')

merged = ET.Element('testsuites')
totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}

for report in reports:
    root = ET.parse(report).getroot()
    suites = [root] if local_name(root.tag) == 'testsuite' else [
        node for node in list(root) if local_name(node.tag) == 'testsuite'
    ]
    for suite in suites:
        merged.append(suite)
        for key in totals:
            totals[key] += int(float(suite.attrib.get(key, 0)))

for key, value in totals.items():
    merged.set(key, str(value))

ET.ElementTree(merged).write(
    'target/xray/chef-krish-junit-results.xml',
    encoding='utf-8',
    xml_declaration=True
)
PY

                                    AUTH_BODY=$(python3 -c 'import json,os; print(json.dumps({"client_id":os.environ["XRAY_CLIENT_ID"],"client_secret":os.environ["XRAY_CLIENT_SECRET"]}))')
                                    XRAY_TOKEN=$(curl --fail --silent --show-error \
                                        -H 'Content-Type: application/json' \
                                        --data "$AUTH_BODY" \
                                        'https://xray.cloud.getxray.app/api/v2/authenticate' | tr -d '"')

                                    if [ -z "$XRAY_TOKEN" ]; then
                                        echo 'Xray authentication returned an empty token.'
                                        exit 3
                                    fi

                                    XRAY_IMPORT_URL="https://xray.cloud.getxray.app/api/v2/import/execution/junit?projectKey=${XRAY_PROJECT_KEY_VALUE}"
                                    if [ -n "${XRAY_TEST_PLAN_KEY_VALUE:-}" ]; then
                                        XRAY_IMPORT_URL="${XRAY_IMPORT_URL}&testPlanKey=${XRAY_TEST_PLAN_KEY_VALUE}"
                                    fi

                                    # Keep Xray's response body even for 4xx/5xx errors. Without
                                    # this, curl only reports "HTTP 400" and hides the useful Jira/
                                    # Xray explanation (for example, project mapping or permissions).
                                    XRAY_HTTP_STATUS=$(curl --silent --show-error \
                                        --output target/xray/import-response.json \
                                        --write-out '%{http_code}' \
                                        -H 'Content-Type: text/xml' \
                                        -H "Authorization: Bearer ${XRAY_TOKEN}" \
                                        --data-binary '@target/xray/chef-krish-junit-results.xml' \
                                        "$XRAY_IMPORT_URL")

                                    XRAY_RESPONSE=$(cat target/xray/import-response.json)
                                    if [ "$XRAY_HTTP_STATUS" -lt 200 ] || [ "$XRAY_HTTP_STATUS" -ge 300 ]; then
                                        echo "Xray import failed with HTTP ${XRAY_HTTP_STATUS}."
                                        echo "Xray response: ${XRAY_RESPONSE}"
                                        exit 4
                                    fi

                                    echo "Xray import completed: ${XRAY_RESPONSE}"
                                '''
                            }
                        }
                        xrayUploadStatus = 'SUCCESS'
                    } catch (err) {
                        xrayUploadStatus = 'FAILED'
                        echo "Xray upload failed. Jenkins reports remain available. Details: ${err}"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }

            script {
                // Build a secret-free summary from the XML reports and send it
                // to n8n. n8n owns recipient routing and notification channels.
                def finalStatus = currentBuild.currentResult ?: 'SUCCESS'

                try {
                    withCredentials([
                        string(credentialsId: 'N8N_WEBHOOK_URL', variable: 'N8N_WEBHOOK_URL'),
                        string(credentialsId: 'N8N_WEBHOOK_SECRET', variable: 'N8N_WEBHOOK_SECRET')
                    ]) {
                        withEnv([
                            "FINAL_BUILD_STATUS=${finalStatus}",
                            "XRAY_UPLOAD_STATUS=${xrayUploadStatus}",
                            "XRAY_PROJECT_KEY_VALUE=${params.XRAY_PROJECT_KEY?.trim() ?: ''}",
                            "XRAY_TEST_PLAN_KEY_VALUE=${params.XRAY_TEST_PLAN_KEY?.trim() ?: ''}"
                        ]) {
                            sh '''
                                set -eu
                                set +x
                                mkdir -p target

                                python3 <<'PY'
import glob
import json
import os
import re
import xml.etree.ElementTree as ET


def local_name(tag):
    return tag.rsplit('}', 1)[-1]


total = failures = errors = skipped = 0
failed_tests = []

for report in glob.glob('target/pipeline-reports/**/*.xml', recursive=True):
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        continue

    if local_name(root.tag) == 'testsuite':
        suites = [root]
    else:
        suites = [node for node in list(root) if local_name(node.tag) == 'testsuite']

    for suite in suites:
        total += int(float(suite.attrib.get('tests', 0)))
        failures += int(float(suite.attrib.get('failures', 0)))
        errors += int(float(suite.attrib.get('errors', 0)))
        skipped += int(float(suite.attrib.get('skipped', 0)))

        for case in suite.iter():
            if local_name(case.tag) != 'testcase':
                continue
            problem = None
            for child in list(case):
                if local_name(child.tag) in ('failure', 'error'):
                    problem = child
                    break
            if problem is not None:
                failed_tests.append({
                    'name': case.attrib.get('name', 'unknown'),
                    'className': case.attrib.get('classname', ''),
                    'message': (problem.attrib.get('message') or (problem.text or '').strip())[:800]
                })

failed = failures + errors
passed = max(total - failed - skipped, 0)
status = os.environ.get('FINAL_BUILD_STATUS', 'UNKNOWN')
xray_status = os.environ.get('XRAY_UPLOAD_STATUS', 'NOT_ATTEMPTED')

failure_text = ' '.join(
    f"{item.get('name', '')} {item.get('message', '')}" for item in failed_tests
).lower()
security_failure = bool(re.search(
    r'gate[ ]*[12]|guardrail|prompt.?injection|jailbreak|allergen',
    failure_text
))
test_failure = failed > 0
build_failure = status == 'FAILURE' and not test_failure
environment_failure = status in ('ABORTED', 'NOT_BUILT') or build_failure
integration_failure = xray_status == 'FAILED'

if security_failure:
    severity = 'HIGH'
    failure_layer = 'APPLICATION_GUARDRAIL'
    owner_group = 'BACKEND_GUARDRAILS'
elif build_failure or environment_failure:
    severity = 'HIGH'
    failure_layer = 'PIPELINE_ENVIRONMENT'
    owner_group = 'DEVOPS'
elif integration_failure:
    severity = 'MEDIUM'
    failure_layer = 'INTEGRATION'
    owner_group = 'QA_AUTOMATION'
elif test_failure:
    severity = 'HIGH'
    failure_layer = 'APPLICATION_TEST'
    owner_group = 'APPLICATION_DEVELOPER'
else:
    severity = 'INFO'
    failure_layer = 'NONE'
    owner_group = 'QA_CHANNEL'

build_url = os.environ.get('BUILD_URL', '')
payload = {
    'event': 'chef-krish.pipeline.completed',
    'project': 'Chef Krish',
    'buildNumber': os.environ.get('BUILD_NUMBER', ''),
    'status': status,
    'severity': severity,
    'failureLayer': failure_layer,
    'ownerGroup': owner_group,
    'flags': {
        'testFailure': test_failure,
        'buildFailure': build_failure,
        'environmentFailure': environment_failure,
        'securityGuardrailFailure': security_failure,
        'integrationFailure': integration_failure,
        'mutatingTestsSkipped': skipped > 0,
        'repeatedFailure': False,
        'secretsExposed': False
    },
    'results': {
        'total': total,
        'passed': passed,
        'failed': failed,
        'skipped': skipped
    },
    'failedTests': failed_tests[:25],
    'xray': {
        'uploadStatus': xray_status,
        'projectKey': os.environ.get('XRAY_PROJECT_KEY_VALUE', ''),
        'testPlanKey': os.environ.get('XRAY_TEST_PLAN_KEY_VALUE', '')
    },
    'links': {
        'jenkinsBuild': build_url,
        'junitReport': f'{build_url}testReport/' if build_url else ''
    }
}

with open('target/n8n-payload.json', 'w', encoding='utf-8') as output:
    json.dump(payload, output, ensure_ascii=False)
PY

                                RESPONSE=$(curl --fail --silent --show-error \
                                    -X POST "$N8N_WEBHOOK_URL" \
                                    -H 'Content-Type: application/json' \
                                    -H "X-Chef-Krish-Webhook-Secret: ${N8N_WEBHOOK_SECRET}" \
                                    --data-binary '@target/n8n-payload.json')
                                echo "n8n accepted the Jenkins result: ${RESPONSE}"
                            '''
                        }
                    }
                } catch (err) {
                    echo "n8n notification failed. Test reports are still archived. Details: ${err}"
                    currentBuild.result = 'UNSTABLE'
                }
            }

            archiveArtifacts(
                artifacts: 'target/pipeline-reports/**,target/xray/**,target/n8n-payload.json',
                allowEmptyArchive: true
            )

            echo "Chef Krish pipeline result: ${currentBuild.currentResult}. Build: ${env.BUILD_URL}"

            // Cleanup is intentionally last. Xray, n8n, and artifact archival
            // must finish before the workspace is removed.
            cleanWs()
        }
    }
}

// Current suite note:
// The repository presently uses TestNG assertions and Selenium UI tests.
// Jenkins' `junit` step can publish Surefire-compatible XML from TestNG, and
// Xray's JUnit importer accepts that XML. Migrating the Java tests themselves
// to JUnit 5 is a separate framework change, not a Jenkinsfile-only change.
