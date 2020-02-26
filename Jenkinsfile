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
            timeout(time: 2, unit: 'HOURS')
            timestamps() 
        }

        environment {
            // Define test branches and device services
            BRANCHLIST = 'master'
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
                            GOARCH = 'amd64'
                            SLAVE = edgex.getNode(config, 'amd64')
                            TAF_COMMOM_IMAGE = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common:latest'
                            USE_DB = '-redis'
                            USE_NO_SECURITY="-no-secty"
                        }
                        steps {
                            script {
                                def rootDir = pwd()
                                def runTestScripts = load "${rootDir}/runTestScripts.groovy" 
                                runTestScripts.main()
                            }
                        }
                    }
                    // stage('arm64'){
                    //      when {
                    //         beforeAgent true
                    //         expression { edgex.nodeExists(config, 'arm64') }
                    //     }
                    //     agent { label edgex.getNode(config, 'arm64') }
                    //     environment {
                    //         ARCH = 'arm64'
                    //         GOARCH = 'arm64'
                    //         SLAVE = edgex.getNode(config, 'arm64')
                    //         TAF_COMMOM_IMAGE = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common-arm64:latest'
                    //         FOR_REDIS = true
                    //         SECURITY_SERVICE_NEEDED = false
                    //     }
                    //     steps {
                    //         script {
                    //             def rootDir = pwd()
                    //             def edgeXFuncTest = load "${rootDir}/edgeXFuncTest.groovy" 
                    //             edgeXFuncTest.parallelBranch()
                    //         }
                    //     }
                    // }
                }
                
            }

            stage ('Publish Robotframework Report...') {
                steps{
                    script {
                        def BRANCHES = "${BRANCHLIST}".split(',')
                        for (z in BRANCHES) {
                            def BRANCH = z
                            echo "Branch:${BRANCH}"
                            unstash "${BRANCH}-report"
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
