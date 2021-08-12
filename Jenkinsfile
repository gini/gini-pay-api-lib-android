#!/usr/bin/env groovy
pipeline {
    agent any
    environment {
        NEXUS_MAVEN = credentials('external-nexus-maven-repo-credentials')
        GIT = credentials('github')
        GINI_API_CREDENTIALS = credentials('gini-vision-library-android_gini-api-client-credentials')
        GINI_ACCOUNTING_API_CREDENTIALS = credentials('gini-vision-library-android_gini-accounting-api-client-credentials')
        JAVA11 = '/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home'
    }
    stages {
        stage('Import Pipeline Libraries') {
            steps{
                library 'android-tools'
            }
        }
        stage('Build') {
            steps {
                sh './gradlew ginipaylib:clean ginipaylib:assembleDebug ginipaylib:assembleRelease -Dorg.gradle.java.home=$JAVA11'
            }
        }
        stage('Create AVDs') {
            steps {
                script {
                    avd.deleteCorrupt()
                    avd.create("api-22-nexus-5x", "system-images;android-22;google_apis;x86", "Nexus 5X")
                    avd.create("api-26-nexus-5x", "system-images;android-26;google_apis;x86", "Nexus 5X")
                }
            }
        }
        stage('Instrumentation Tests - API Level 26') {
            steps {
                script {
                    def emulatorPort = emulator.start(avd.createName("api-26-nexus-5x"), "nexus_5x", '''\
                        -prop persist.sys.language=en -prop persist.sys.country=US -gpu on -camera-back emulated \
                        -no-snapshot-save -no-snapshot-load -no-audio -no-window
                    ''')
                    sh "echo $emulatorPort > emulator_port"
                    adb.setAnimationDurationScale("emulator-$emulatorPort", 0)
                    withEnv(["EMULATOR_PORT=$emulatorPort", "PATH+TOOLS=$ANDROID_HOME/tools", "PATH+TOOLS_BIN=$ANDROID_HOME/tools/bin", "PATH+PLATFORM_TOOLS=$ANDROID_HOME/platform-tools"]) {
                        sh '''
                            ANDROID_SERIAL=emulator-$EMULATOR_PORT ./gradlew ginipaylib:connectedAndroidTest \
                            -PtestClientId=$GINI_API_CREDENTIALS_USR -PtestClientSecret=$GINI_API_CREDENTIALS_PSW \
                            -PtestClientIdAccounting=$GINI_ACCOUNTING_API_CREDENTIALS_USR -PtestClientSecretAccounting=$GINI_ACCOUNTING_API_CREDENTIALS_PSW \
                            -PtestApiUri='https://pay-api.gini.net' -PtestApiUriAccounting='https://accounting-api.gini.net' \
                            -PtestUserCenterUri='https://user.gini.net' \
                            -Dorg.gradle.java.home=$JAVA11
                        '''
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'ginipaylib/build/outputs/androidTest-results/targeted/*.xml'
                    script {
                        def emulatorPort = sh returnStdout:true, script: 'cat emulator_port'
                        emulatorPort = emulatorPort.trim().replaceAll("\r", "").replaceAll("\n", "")
                        emulator.stop(emulatorPort)
                        sh 'rm emulator_port || true'
                    }
                }
            }
        }
        stage('Instrumentation Tests - API Level 22') {
            steps {
                script {
                    def emulatorPort = emulator.start(avd.createName("api-22-nexus-5x"), "nexus_5x", '''\
                        -prop persist.sys.language=en -prop persist.sys.country=US -gpu on -camera-back emulated \
                        -no-snapshot-save -no-snapshot-load -no-audio -no-window
                    ''')
                    sh "echo $emulatorPort > emulator_port"
                    adb.setAnimationDurationScale("emulator-$emulatorPort", 0)
                    withEnv(["EMULATOR_PORT=$emulatorPort", "PATH+TOOLS=$ANDROID_HOME/tools", "PATH+TOOLS_BIN=$ANDROID_HOME/tools/bin", "PATH+PLATFORM_TOOLS=$ANDROID_HOME/platform-tools"]) {
                        sh '''
                            ANDROID_SERIAL=emulator-$EMULATOR_PORT ./gradlew ginipaylib:connectedAndroidTest \
                            -PtestClientId=$GINI_API_CREDENTIALS_USR -PtestClientSecret=$GINI_API_CREDENTIALS_PSW \
                            -PtestClientIdAccounting=$GINI_ACCOUNTING_API_CREDENTIALS_USR -PtestClientSecretAccounting=$GINI_ACCOUNTING_API_CREDENTIALS_PSW \
                            -PtestApiUri='https://pay-api.gini.net' -PtestApiUriAccounting='https://accounting-api.gini.net' \
                            -PtestUserCenterUri='https://user.gini.net' \
                            -Dorg.gradle.java.home=$JAVA11
                        '''
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'ginipaylib/build/outputs/androidTest-results/targeted/*.xml'
                    script {
                        def emulatorPort = sh returnStdout:true, script: 'cat emulator_port'
                        emulatorPort = emulatorPort.trim().replaceAll("\r", "").replaceAll("\n", "")
                        emulator.stop(emulatorPort)
                        sh 'rm emulator_port || true'
                    }
                }
            }
        }
        stage('Build Documentation') {
            when {
                expression {
                  def tag = sh(returnStdout: true, script: 'git tag --contains $(git rev-parse HEAD)').trim()
                  return !tag.isEmpty()
                }
            }
            steps {
                withEnv(["PATH+=/usr/local/bin"]) {
                    sh './gradlew ginipaylib:dokkaHtml -Dorg.gradle.java.home=$JAVA11'
                    sh 'scripts/generate-sphinx-doc.sh'
                }
            }
        }
        stage('Release Documentation') {
            when {
                expression {
                    def tag = sh(returnStdout: true, script: 'git tag --contains $(git rev-parse HEAD)').trim()
                    return !tag.isEmpty()
                }
                expression {
                    boolean publish = false
                    try {
                        def sha = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        input "Release documentation from branch ${env.BRANCH_NAME} commit ${sha}?"
                        publish = true
                    } catch (final ignore) {
                        publish = false
                    }
                    return publish
                }
            }
            steps {
                sh 'scripts/release-doc.sh $GIT_USR $GIT_PSW'
            }
        }
        stage('Release Library') {
            when {
                expression {
                    def tag = sh(returnStdout: true, script: 'git tag --contains $(git rev-parse HEAD)').trim()
                    return !tag.isEmpty()
                }
                expression {
                    boolean publish = false
                    try {
                        def sha = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        input "Release from branch ${env.BRANCH_NAME} commit ${sha}?"
                        publish = true
                    } catch (final ignore) {
                        publish = false
                    }
                    return publish
                }
            }
            steps {
                sh '''
                    ./gradlew ginipaylib:publishReleasePublicationToOpenRepository \
                    -PmavenOpenRepoUrl=https://repo.gini.net/nexus/content/repositories/open \
                    -PrepoUser=$NEXUS_MAVEN_USR -PrepoPassword=$NEXUS_MAVEN_PSW' \
                    -Dorg.gradle.java.home=$JAVA11
                '''
            }
        }
    }
}
