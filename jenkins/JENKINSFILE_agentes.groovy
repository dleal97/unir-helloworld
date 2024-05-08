pipeline {
    agent none

    stages {
        stage('Get Code') {
            //agent { node 'agent-aux' }
            agent any
            steps {
                git 'https://github.com/dleal97/unir-helloworld.git', branch:'develop'
                stash name: 'code', includes: '**'
                echo 'Código guardado en el stash.'
                echo "${env.NODE_NAME}"
            }
        }
        
        stage('Build') {
            //agent { node 'agent-aux' }
            agent any
            steps {
                unstash 'code'
                echo 'Esto es Python, no hay que compilar nada!!'
                echo WORKSPACE
                bat '''
                    whoami
                    hostname
                    dir
                '''
            }
        }
        
        stage('Test') {
            parallel {
                stage('Unit') {
                    agent { node 'agent-aux2' }
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            unstash 'code'
                            echo "${env.NODE_NAME}" 
                            bat '''
                                whoami
                                hostname
                                set PYTHONPATH=%WORKSPACE%
                                \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\pytest.exe --junitxml=result-unit.xml test/unit
                            '''
                            stash name:'unit-res', includes:'result-unit.xml'
                        }
                    }
                }
        
                stage('Rest') {
                    agent { node 'agent-aux3' }
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            unstash 'code'
                            bat '''
                                whoami
                                hostname
                                set FLASK_APP=app\\api.py
                                start \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\flask.exe run
                                start java -jar \\Users\\dleal\\Documents\\Kschool\\CURSO\\Wiremock\\wiremock-standalone-3.5.4 -port 9090 --root-dir test/wiremock
                            '''
                            script {
                                def urls = ['http://localhost:5000', 'http://localhost:9090']
                                boolean connected = false

                                while (!connected) {
                                    for (url in urls) {
                                        try {
                                            def response = sh(script: "curl -s -o /dev/null -w '%{http_code}' ${url}", returnStdout: true).trim()
                                            if (response == '200') {
                                                println "Conexión establecida con ${url}"
                                                connected = true
                                            } else {
                                                println "No se pudo establecer conexión con ${url}. Código de estado HTTP: ${response}"
                                            }
                                        } catch (Exception e) {
                                            println "Error al intentar conectar con ${url}: ${e.message}"
                                        }
                                    }
                                    if (!connected) {
                                        println "Intentando nuevamente en 10 segundos..."
                                        sleep(time: 10, unit: 'SECONDS')
                                    }
                                }
                            }
                                                      
                            bat '''   
                                set PYTHONPATH=.
                                \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\pytest.exe --junitxml=result-rest.xml test/rest
                            ''' 
                            stash name:'rest-res', includes:'result-res.xml'      
                        }
                    }
                }               
            }
        }
        
        stage('Results') {
            agent any
            steps {
                unstash name:'unit-res'
                unstash name:'rest-res'
                junit 'result*.xml'
                echo 'FINISH!!'
            }
        }
    }
    
    post {
        always {
            clearWs()
        }
    }
}
