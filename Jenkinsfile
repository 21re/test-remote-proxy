@Library("21re") _
gen.init()

publish = false
if(env.BRANCH_NAME == 'master') {
    publish = true
}

node {
    checkout scm

    sbtBuild([cmds: "clean compile test assembly"])

    sh """
      docker build -t registry.control.21re.works/local-selenium:chromium-${gen.VERSION} -f Dockerfile.chromium .
      docker build -t registry.control.21re.works/local-selenium:firefox-${gen.VERSION} -f Dockerfile.firefox .
    """

    if(publish) {
      sbtBuild([cmds: "publish"])

      sh """
        docker tag registry.control.21re.works/local-selenium:chromium-${gen.VERSION} registry.control.21re.works/local-selenium:chromium-latest
        docker push registry.control.21re.works/local-selenium:chromium-${gen.VERSION}
        docker push registry.control.21re.works/local-selenium:chromium-latest

        docker tag registry.control.21re.works/local-selenium:firefox-${gen.VERSION} registry.control.21re.works/local-selenium:firefox-latest
        docker push registry.control.21re.works/local-selenium:firefox-${gen.VERSION}
        docker push registry.control.21re.works/local-selenium:firefox-latest
      """
    }
}
