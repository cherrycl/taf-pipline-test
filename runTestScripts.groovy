
def main() {
    def BRANCHES = "${BRANCHLIST}".split(',')
    def PROFILES = "${PROFILELIST}".split(',')
    
    def runbranchstage = [:]

    for (x in BRANCHES) {
        def BRANCH = x
        
        runbranchstage["Test ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}"]= {
            node("${SLAVE}") {
                stage ('Checkout repository') {
                    checkout([$class: 'GitSCM',
                        branches: [[name: "*/${BRANCH}"]],
                        doGenerateSubmoduleConfigurations: false, 
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']], 
                        submoduleCfg: [], 
                        userRemoteConfigs: [[url: 'https://github.com/cherrycl/edgex-taf.git']]
                        ])
                }

                stage ('Deploy EdgeX') {
                    dir ('TAF/utils/scripts/docker') {
                        if ("${USE_SECURITY}" != '-security-') {
                            sh "sh get-compose-file.sh ${USE_DB} ${ARCH}"
                        } else {
                            sh "sh get-compose-file.sh ${USE_DB} ${ARCH} ${USE_SECURITY}"
                        }
                        
                        sh 'ls *.yaml *.yml'
                    }

                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped -u functionalTest/deploy-edgex.robot -p default"
                }

                echo "Profiles : ${PROFILES}"
                stage ('Run Test Script') {
                    script {
                        for (y in PROFILES) {
                            def profile = y
                            echo "Profile : ${profile}"
                            echo '===== Deploy Device Service ====='
                            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                    --exclude Skipped -u functionalTest/device-service/deploy_device_service.robot -p ${profile}"

                            echo '===== Run Device Service Test Case ====='
                            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                    --exclude Skipped -u functionalTest/device-service/common -p ${profile}"
                            
                            dir ('TAF/testArtifacts/reports/rename-report') {
                                sh "cp ../edgex/log.html ${profile}-common-log.html"
                                sh "cp ../edgex/report.xml ${profile}-common-report.xml"
                            }

                            echo '===== Shutdown Device Service ====='
                            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                    --exclude Skipped -u functionalTest/device-service/shutdown_device_service.robot -p ${profile}"
                        }
                    }
                    
                    echo '===== Merge Reports ====='
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                rebot --inputdir TAF/testArtifacts/reports/rename-report \
                                --outputdir TAF/testArtifacts/reports/${BRANCH}-report"

                    dir ("TAF/testArtifacts/reports/${BRANCH}-report") {
                        sh "mkdir ../merged-report"
                        sh "cp log.html ../merged-report/${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-log.html"
                        sh "cp result.xml ../merged-report/${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-report.xml"
                    }
                    stash name: "${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-report", includes: "TAF/testArtifacts/reports/merged-report/*"
                }
                stage ('Shutdown EdgeX') {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped -u functionalTest/shutdown.robot -p default"
                }
            }
        }
    }
    parallel runbranchstage
}

return this
