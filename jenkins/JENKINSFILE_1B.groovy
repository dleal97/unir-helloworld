pipeline {
    agent any
    
    stages {
        stage('Get Code') {
            steps {
                //git 'https://github.com/dleal97/unir-helloworld.git'
                git branch: 'feature_fix_coverage', url: 'https://github.com/dleal97/unir-helloworld.git'
            }
        }
        
        stage('Unit') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                   bat '''
                        set PYTHONPATH=%WORKSPACE%
                        \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\pytest.exe --junitxml=result-unit.xml test/unit
                    '''      
                    junit 'result*.xml'
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
        stage('Static') {
            steps {
                bat '''
                    \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\flake8.exe --exit-zero --format=pylint app >flake8.out
                '''
                recordIssues tools: [flake8(name: 'Flake8', pattern: 'flake8.out')], qualityGates: [[threshold: 8, type: 'TOTAL', unstable: true], [threshold: 10, type: 'TOTAL', unstable: false]]
            }
        }
        
        stage('Security') {
            steps {
                bat '''
                    \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\bandit.exe --exit-zero -r . -f custom -o bandit.out --severity-level medium --msg-template "{abspath}:{line}: {severity}: {test_id}: {msg}"
                '''
                recordIssues tools: [pyLint(name: 'Bandit', pattern: 'bandit.out')], qualityGates: [[threshold: 1, type: 'TOTAL', unstable: true], [threshold: 2, type: 'TOTAL', unstable: false]]
            }
        }
        
        stage('Coverage') {
            steps { 
                bat '''
                \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\coverage.exe run --branch --source=app --omit=app\\__init__.py,app\\api.py -m pytest test\\unit
                \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\coverage.exe xml
                '''
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    cobertura coberturaReportFile: 'coverage.xml', onlyStable: false, failUnstable: false, conditionalCoverageTargets: '100,80,90', lineCoverageTargets: '100,85,95'
                }
            }
        }

        stage('Performance') {
            steps {
                bat '''
                set FLASK_APP=app\\api.py
                start \\Users\\dleal\\AppData\\Local\\Programs\\Python\\Python39\\Scripts\\flask.exe run
                \\Users\\dleal\\Downloads\\apache-jmeter-5.6.3\\apache-jmeter-5.6.3\\bin\\jmeter -n -t test\\jmeter\\practico2b.jmx -f -l flask.jtl
                '''
                perfReport sourceDataFiles: 'flask.jtl'
            }
        }
    }
}
