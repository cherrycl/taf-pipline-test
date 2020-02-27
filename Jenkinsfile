#!/usr/bin/env groovy

pipeline {
    agent { label "docker-arm64" }
    options { timestamps() }
    environment {
            // Define test branches and device services
        //BRANCHLIST = 'master'
        PROFILELIST = 'device-virtual,device-modbus'
        ARCH = 'arm64'
        //GOARCH = 'amd64'
        //SLAVE = edgex.getNode(config, 'amd64')
        TAF_COMMOM_IMAGE= 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common-arm64:latest'
        COMPOSE_IMAGE='docker/compose:alpine-1.25.4-rc2'
        USE_DB = 'redis'
        USE_SECURITY = '-'
    }
    stages {
        stage ('Checkout out master branch from edgex-taf') {
            steps {
                checkout([$class: 'GitSCM',
                    branches: [[name: '*/issue-43']], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [[url: 'https://github.com/cherrycl/edgex-taf.git']]
                ])
            }
        }

        stage ('Deploy EdgeX') {
            steps {
                script {
                    dir ('TAF/utils/scripts/docker') {
                        sh "sh get-compose-file.sh ${USE_DB} ${ARCH} ${USE_SECURITY}"
                        sh 'ls *.yaml *.yml'
                    }
                }
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE} -w ${env.WORKSPACE} \
                        -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                        --exclude Skipped -u functionalTest/deploy-edgex.robot -p default"
            }
        }
        stage ('Run Test Script') {
            steps {
                script {
                    def PROFILES = "${PROFILELIST}".split(',')
                    for (x in PROFILES) {
                        def profile = x
                        echo '===== Deploy Device Service ====='
                        sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE} -w ${env.WORKSPACE} \
                                -e ARCH=${ARCH} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                --exclude Skipped -u functionalTest/device-service/deploy_device_service.robot -p ${profile}"

                        echo '===== Run Device Service Test Case ====='
                        sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE} -w ${env.WORKSPACE} \
                                -e ARCH=${ARCH} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                --exclude Skipped -u functionalTest/device-service/common -p ${profile}"

                        dir ('TAF/testArtifacts/reports/') {
                            stash name: "report-${profile}", includes: "edgex/*"

                        }
                        dir ('TAF/testArtifacts/reports/rename-report') {
                            unstash "report-${profile}"
                            sh "mv edgex/log.html ${profile}-common-log.html"
                            sh "mv edgex/report.xml ${profile}-common-report.xml"
                        }

                        echo '===== Shutdown Device Service ====='
                        sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE} -w ${env.WORKSPACE} \
                                -e ARCH=${ARCH} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                --exclude Skipped -u functionalTest/device-service/deploy_device_service.robot -p ${profile}"
                    }
                }
                echo '===== Merge Reports ====='
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE} -w ${env.WORKSPACE} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            rebot --inputdir TAF/testArtifacts/reports/rename-report --outputdir TAF/testArtifacts/reports/merged-report"
            }
        }
        stage('Clean up') {
            steps {
                script {
                    try {
                        sh 'docker kill $(docker ps -aq)'
                        sh 'docker rmi -f $(docker images -aq)'
                    } catch (e) {
                        echo "Clean up error!"
                    } finally {
                        sh 'docker system prune -f -a'
                        sh 'docker volume prune -f'
                    }
                }
            }
        }
        stage ('Publish Robotframework Report...') {
            steps{
                echo 'Publish....'

                publishHTML(
                    target: [
                        allowMissing: false,
                        keepAll: false,
                        reportDir: 'TAF/testArtifacts/reports/merged-report',
                        reportFiles: 'log.html',
                        reportName: 'Functional Test Reports']
                )

                junit 'TAF/testArtifacts/reports/merged-report/*.xml'
            }
        }
    }
}
