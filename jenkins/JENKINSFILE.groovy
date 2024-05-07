pipeline {
    agent any

    stages {
        stage('Get Code') {
            steps {
                //git 'https://github.com/anieto-unir/helloworld.git'
                git 'https://github.com/dleal97/unir-helloworld.git'
            }
        }
        
        stage('Build') {
            steps {
                echo 'Esto es Python, no hay que compilar nada!!'
                echo WORKSPACE
                bat 'dir'
            }
        }
        
        stage('Test') {
            parallel {
                stage('Unit') {
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                           bat '''
                                set PYTHONPATH=%WORKSPACE%
                                \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\pytest.exe --junitxml=result-unit.xml test/unit
                            '''                    
                        }
                    }
                }
        
                stage('Rest') {
                    steps {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            bat '''
                                set FLASK_APP=app\\api.py
                                start \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\flask.exe run
                                start java -jar \\Users\\dleal\\Documents\\Kschool\\CURSO\\Wiremock\\wiremock-standalone-3.5.4.jar -port 9090 --root-dir test/wiremock

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
                        }
                    }
                }               
            }
        }
        
        stage('Results') {
            steps {
                junit 'result*.xml'
                echo 'FINISH!!'
            }
        }
    }
}
