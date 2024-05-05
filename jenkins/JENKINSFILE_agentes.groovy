pipeline {
    agent none

    stages {
        stage('Get Code') {
            //agent { node 'agent-aux' }
            agent any
            steps {
                git 'https://github.com/dleal97/unir-helloworld.git'
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
                                
                                set PYTHONPATH=.
                                \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\pytest.exe --junitxml=result-rest.xml test/rest
                            '''    
                        }
                    }
                }               
            }
        }
        
        stage('Results') {
            agent { node 'agent-aux' }
            steps {
                unstash 'code'
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
