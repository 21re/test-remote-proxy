@Library("21re") _
gen.init()

publish = false
if(env.BRANCH_NAME == 'master') {
    publish = true
}

node {
    checkout scm

    sbtBuild([cmds: "clean compile test assembly"])

    if(publish) {
      sbtBuild([cmds: "publish"])
    }
}
