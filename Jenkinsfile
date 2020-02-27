#!/usr/bin/env groovy
def LOGFILES
def cfgAmd64
def cfgArm64

call([
    project: 'funcational-test',
    mavenSettings: ['functional-testing-settings:SETTINGS_FILE']
])

def call(config) {
    edgex.bannerMessage "[functional-testing] RAW Config: ${config}"

    edgeXGeneric.validate(config)
    edgex.setupNodes(config)

    def _envVarMap = edgeXGeneric.toEnvironment(config)

    pipeline {
        agent { label edgex.mainNode(config) }
        options { 
            timeout(time: 1, unit: 'HOURS')
            timestamps() 
        }

        environment {
            // Define test branches and device services
            BRANCHLIST = 'issue-43'
            PROFILELIST = 'device-virtual,device-modbus'
        }

        stages {
            stage ('Run Test') {
                parallel {
                    stage('amd64-redis'){
                         when {
                            beforeAgent true
                            expression { edgex.nodeExists(config, 'amd64') }
                        }
                        environment {
                            ARCH = 'x86_64'
                            SLAVE = edgex.getNode(config, 'amd64')
                            TAF_COMMOM_IMAGE = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common:latest'
                            COMPOSE_IMAGE='docker/compose:1.25.4'
                            USE_DB = '-redis'
                            // Environment doesn't support empty variable, so adding '-' to represent
                            USE_SECURITY ='-'
                        }
                        steps {
                            script {
                                def rootDir = pwd()
                                def runTestScripts = load "${rootDir}/runTestScripts.groovy" 
                                runTestScripts.main()
                            }
                        }
                    }
                    // stage('amd64-mongo'){
                    //      when {
                    //         beforeAgent true
                    //         expression { edgex.nodeExists(config, 'amd64') }
                    //     }
                    //     environment {
                    //         ARCH = 'x86_64'
                    //         SLAVE = edgex.getNode(config, 'amd64')
                    //         TAF_COMMOM_IMAGE = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common:latest'
                    //         COMPOSE_IMAGE='docker/compose:1.25.4'
                    //         USE_DB = '-mongo'
                    //         USE_SECURITY='-'
                    //     }
                    //     steps {
                    //         script {
                    //             def rootDir = pwd()
                    //             def runTestScripts = load "${rootDir}/runTestScripts.groovy" 
                    //             runTestScripts.main()
                    //         }
                    //     }
                    // }
                    // stage('amd64-mongo-security'){
                    //      when {
                    //         beforeAgent true
                    //         expression { edgex.nodeExists(config, 'amd64') }
                    //     }
                    //     environment {
                    //         ARCH = 'x86_64'
                    //         SLAVE = edgex.getNode(config, 'amd64')
                    //         TAF_COMMOM_IMAGE = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common:latest'
                    //         COMPOSE_IMAGE = 'docker/compose:1.26.0-rc2'
                    //         USE_DB = '-mongo'
                    //         USE_SECURITY = '-security-'
                    //     }
                    //     steps {
                    //         script {
                    //             def rootDir = pwd()
                    //             def runTestScripts = load "${rootDir}/runTestScripts.groovy" 
                    //             runTestScripts.main()
                    //         }
                    //     }
                    // }
                    stage('arm64-redis'){
                         when {
                            beforeAgent true
                            expression { edgex.nodeExists(config, 'arm64') }
                        }
                        agent { label edgex.getNode(config, 'arm64') }
                        environment {
                            ARCH = 'arm64'
                            SLAVE = edgex.getNode(config, 'arm64')
                            TAF_COMMOM_IMAGE = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common-arm64:latest'
                            COMPOSE_IMAGE='docker/compose:1.26.0-rc2'
                            USE_DB = '-redis'
                            // Environment doesn't support empty variable, so adding '-' to represent
                            USE_SECURITY ='-'
                        }
                        steps {
                            script {
                                def rootDir = pwd()
                                def edgeXFuncTest = load "${rootDir}/edgeXFuncTest.groovy" 
                                edgeXFuncTest.parallelBranch()
                            }
                        }
                    }
                }
                
            }

            stage ('Publish Robotframework Report...') {
                steps{
                    script {
                        def BRANCHES = "${BRANCHLIST}".split(',')
                        for (z in BRANCHES) {
                            def BRANCH = z
                            unstash "x86_64-redis-${BRANCH}-report"
                            // unstash "x86_64-mongo-${BRANCH}-report"
                            // unstash "x86_64-mongo-security-${BRANCH}-report"
                        }
                    
                        dir ('TAF/testArtifacts/reports/merged-report/') {
                            LOGFILES= sh (
                                script: 'ls *-log.html | sed ":a;N;s/\\n/,/g;ta"',
                                returnStdout: true
                            )
                        }
                    }
                    
                    publishHTML(
                        target: [
                            allowMissing: false,
                            keepAll: false,
                            reportDir: 'TAF/testArtifacts/reports/merged-report',
                            reportFiles: "${LOGFILES}",
                            reportName: 'Functional Test Reports']
                    )

                    junit 'TAF/testArtifacts/reports/merged-report/**.xml'
                }
            }
        }
    }
}
