def parallelBranch() {
    def BRANCHES = "${BRANCHLIST}".split(',')
    def PROFILES = "${PROFILELIST}".split(',')
    //def SLAVES = "${SLAVELIST}".split(',')
    
    echo '======== Start to parallel Test ========'
    //def maps = (0..<Math.min(BRANCHES.size(), SLAVES.size())).collect { i -> [branch: BRANCHES[i], slave: SLAVES[i]] }
    //def runbranchstage = [:]

    for (item in BRANCHES) {
        def BRANCH = item
        //def SLAVE = item.slave
        
        runbranchstage["Run Test on ${ARCH} For Branch : ${BRANCH}"]= {
            node("edgex-client") {
                stage ('Checkout repository') {
                    checkout([$class: 'GitSCM',
                        branches: [[name: "*/${BRANCH}"]],
                        doGenerateSubmoduleConfigurations: false, 
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']], 
                        submoduleCfg: [], 
                        userRemoteConfigs: [[url: 'https://github.com/edgexfoundry/edgex-taf.git']]
                        ])
                }

                stage ('Deploy EdgeX') {
                    dir ('TAF/utils/scripts/docker') {
                        sh 'sh get-compose-file.sh'
                    }

                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
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
                                    -e ARCH=${ARCH} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                    --exclude Skipped -u functionalTest/device-service/deploy_device_service.robot -p ${profile}"

                            echo '===== Run Device Service Test Case ====='
                            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                    -e ARCH=${ARCH} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                    --exclude Skipped -u functionalTest/device-service/common -p ${profile}"

                            
                            dir ('TAF/testArtifacts/reports/rename-report') {
                                sh "cp ../edgex/log.html ${profile}-common-log.html"
                                sh "cp ../edgex/report.xml ${profile}-common-report.xml"
                            }

                            echo '===== Shutdown Device Service ====='
                            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                    -e ARCH=${ARCH} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                    --exclude Skipped -u functionalTest/device-service/shutdown_device_service.robot -p ${profile}"
                        }
                    }
                    
                    echo '===== Merge Reports ====='
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                rebot --inputdir TAF/testArtifacts/reports/rename-report \
                                --outputdir TAF/testArtifacts/reports/${BRANCH}-report"

                    dir ("TAF/testArtifacts/reports/${BRANCH}-report") {
                        sh "mkdir ../merged-report"
                        sh "cp log.html ../merged-report/${ARCH}-${BRANCH}-branch-log.html"
                        sh "cp result.xml ../merged-report/${ARCH}-${BRANCH}-branch-report.xml"
                    }
                    stash name: "${BRANCH}-report", includes: "TAF/testArtifacts/reports/merged-report/*"
                }
                stage ('Shutdown EdgeX') {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped -u functionalTest/shutdown.robot -p default"
                }
            }
        }
    }
    parallel runbranchstage
}

return this
