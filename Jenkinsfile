pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        choice(
            name: 'BUILD_VARIANT',
            choices: ['debug', 'release'],
            description: 'debug = installation interne ; release = minify ProGuard'
        )
        choice(
            name: 'APPS',
            choices: ['both', 'gateway', 'distributor'],
            description: 'Applications à compiler'
        )
        booleanParam(
            name: 'RUN_UNIT_TESTS',
            defaultValue: true,
            description: 'Exécuter les tests JVM (gateway-app)'
        )
        booleanParam(
            name: 'USE_FIREBASE_CREDENTIALS',
            defaultValue: false,
            description: 'Secret files Jenkins pour google-services.json (sinon stubs CI du repo)'
        )
        booleanParam(
            name: 'SIGN_RELEASE',
            defaultValue: false,
            description: 'Signer release (credentials android-keystore* requis)'
        )
        booleanParam(
            name: 'DEPLOY_APK',
            defaultValue: false,
            description: 'Copier les APK sur le VPS'
        )
        string(
            name: 'DEPLOY_HOST',
            defaultValue: 'adminubuntu@osgateway.olive-services.net',
            description: 'SSH user@host'
        )
        string(
            name: 'DEPLOY_APK_DIR',
            defaultValue: '/home/adminubuntu/OliveApps/OSGATEWAY/apk',
            description: 'Répertoire distant pour les APK'
        )
        string(
            name: 'APP_VERSION_NAME',
            defaultValue: '1.0.0',
            description: 'versionName (ex. 1.2.3)'
        )
        string(
            name: 'ANDROID_DOCKER_IMAGE',
            defaultValue: 'ghcr.io/cirruslabs/android-sdk:36',
            description: 'Image Docker SDK si ANDROID_HOME absent sur l’agent'
        )
    }

    environment {
        APP_NAME              = 'mobile-osgateway'
        OSG_ANDROID_BUILD_DIR = "${WORKSPACE}/.android-build"
        GRADLE_USER_HOME      = "${WORKSPACE}/.gradle-cache"
        APP_VERSION_NAME      = "${params.APP_VERSION_NAME}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Firebase config') {
            steps {
                script {
                    if (params.USE_FIREBASE_CREDENTIALS) {
                        withCredentials([
                            file(
                                credentialsId: 'osgateway-google-services-gateway',
                                variable: 'GS_GATEWAY',
                            ),
                            file(
                                credentialsId: 'osgateway-google-services-distributor',
                                variable: 'GS_DISTRIBUTOR',
                            ),
                        ]) {
                            sh '''
                            set -e
                            cp "$GS_GATEWAY" gateway-app/google-services.json
                            cp "$GS_DISTRIBUTOR" distributor-app/google-services.json
                            '''
                        }
                    } else {
                        sh '''
                        set -e
                        cp deploy/jenkins/google-services.gateway.stub.json gateway-app/google-services.json
                        cp deploy/jenkins/google-services.distributor.stub.json distributor-app/google-services.json
                        '''
                    }
                }
            }
        }

        stage('Gradle Build') {
            steps {
                script {
                    def tasks = []
                    if (params.RUN_UNIT_TESTS) {
                        tasks << ':gateway-app:testDebugUnitTest'
                    }
                    def assemble = params.BUILD_VARIANT == 'release' ? 'assembleRelease' : 'assembleDebug'
                    switch (params.APPS) {
                        case 'gateway':
                            tasks << ":gateway-app:${assemble}"
                            break
                        case 'distributor':
                            tasks << ":distributor-app:${assemble}"
                            break
                        default:
                            tasks << ":gateway-app:${assemble}"
                            tasks << ":distributor-app:${assemble}"
                    }
                    def gradleTasks = tasks.join(' ')
                    def runBuild = {
                        sh """
                        set -e
                        chmod +x gradlew
                        export OSG_ANDROID_BUILD_DIR='${OSG_ANDROID_BUILD_DIR}'
                        export GRADLE_USER_HOME='${GRADLE_USER_HOME}'
                        export BUILD_NUMBER='${env.BUILD_NUMBER}'
                        export APP_VERSION_NAME='${params.APP_VERSION_NAME}'

                        run_gradle() {
                          if [ -n "\${ANDROID_HOME:-}" ] && [ -d "\${ANDROID_HOME}" ]; then
                            ./gradlew --no-daemon ${gradleTasks}
                          else
                            KS="/project/.ci-release.keystore"
                            if [ ! -f "\${ANDROID_KEYSTORE_FILE:-}" ]; then
                              KS=""
                            fi
                            docker run --rm \\
                              -v "\${WORKSPACE}:/project" \\
                              -w /project \\
                              -e OSG_ANDROID_BUILD_DIR=/project/.android-build \\
                              -e GRADLE_USER_HOME=/project/.gradle-cache \\
                              -e BUILD_NUMBER \\
                              -e APP_VERSION_NAME \\
                              -e ANDROID_KEYSTORE_FILE="\${KS:-}" \\
                              -e ANDROID_KEYSTORE_PASSWORD \\
                              -e ANDROID_KEY_ALIAS \\
                              -e ANDROID_KEY_PASSWORD \\
                              ${params.ANDROID_DOCKER_IMAGE} \\
                              bash -lc "./gradlew --no-daemon ${gradleTasks}"
                          fi
                        }
                        run_gradle
                        """
                    }

                    if (params.BUILD_VARIANT == 'release' && params.SIGN_RELEASE) {
                        withCredentials([
                            file(credentialsId: 'android-keystore', variable: 'KEYSTORE_FILE'),
                            string(credentialsId: 'android-keystore-password', variable: 'KEYSTORE_PASS'),
                            string(credentialsId: 'android-key-alias', variable: 'KEY_ALIAS'),
                            string(credentialsId: 'android-key-password', variable: 'KEY_PASS'),
                        ]) {
                            sh '''
                            set -e
                            cp "$KEYSTORE_FILE" "${WORKSPACE}/.ci-release.keystore"
                            '''
                            withEnv([
                                "ANDROID_KEYSTORE_FILE=${WORKSPACE}/.ci-release.keystore",
                                "ANDROID_KEYSTORE_PASSWORD=${KEYSTORE_PASS}",
                                "ANDROID_KEY_ALIAS=${KEY_ALIAS}",
                                "ANDROID_KEY_PASSWORD=${KEY_PASS}",
                            ]) {
                                runBuild()
                            }
                        }
                    } else {
                        runBuild()
                    }
                }
            }
        }

        stage('Collect APK') {
            steps {
                sh """
                set -e
                VARIANT='${params.BUILD_VARIANT}'
                rm -rf artifacts
                mkdir -p artifacts
                find "\${OSG_ANDROID_BUILD_DIR}" -path "*/outputs/apk/\${VARIANT}/*.apk" -type f | while read -r apk; do
                  base=\$(basename "\$apk" .apk)
                  cp "\$apk" "artifacts/\${base}.apk"
                  cp "\$apk" "artifacts/\${base}-b${env.BUILD_NUMBER}.apk"
                done
                ls -la artifacts/
                test "\$(ls -1 artifacts/*.apk 2>/dev/null | wc -l)" -gt 0
                """
                archiveArtifacts artifacts: 'artifacts/*.apk', fingerprint: true, allowEmptyArchive: false
            }
        }

        stage('Deploy APK VPS') {
            when {
                expression { params.DEPLOY_APK }
            }
            steps {
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'ssh-server-credentials',
                    keyFileVariable: 'SSH_KEY',
                )]) {
                    sh """
                    set -e
                    chmod 600 "\$SSH_KEY"
                    ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=no ${params.DEPLOY_HOST} \\
                      "mkdir -p ${params.DEPLOY_APK_DIR} && chmod 755 ${params.DEPLOY_APK_DIR}"
                    scp -i "\$SSH_KEY" -o StrictHostKeyChecking=no artifacts/*.apk \\
                      ${params.DEPLOY_HOST}:${params.DEPLOY_APK_DIR}/
                    ssh -i "\$SSH_KEY" -o StrictHostKeyChecking=no ${params.DEPLOY_HOST} \\
                      "chmod 644 ${params.DEPLOY_APK_DIR}/*.apk"
                    """
                }
            }
        }
    }

    post {
        success {
            echo "✅ ${APP_NAME} — artifacts/*.apk (build ${env.BUILD_NUMBER}, ${params.BUILD_VARIANT})"
        }
        failure {
            echo "❌ ${APP_NAME} échoué"
        }
        always {
            cleanWs(deleteDirs: true, notFailBuild: true)
        }
    }
}
