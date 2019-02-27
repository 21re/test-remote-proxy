lazy val server = (project in file("server"))

lazy val root = (project in file(".")).aggregate(server)

name := "remote-test-proxy"

organization := "de.21re"

version := {
  "0.1-" + sys.props.get("BUILD_NUMBER").orElse(sys.env.get("BUILD_NUMBER")).getOrElse("SNAPSHOT")
}


scalaVersion := "2.12.8"
scalaVersion in ThisBuild := "2.12.8"

enablePlugins(JavaAppPackaging, DockerPlugin)

scalacOptions in GlobalScope ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
  "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
  "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
  "-Xlint:option-implicit", // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match", // Pattern match may not be typesafe.
  "-Ypartial-unification", // Enable partial unification in type constructor inference
  "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit" // Warn when nullary methods return Unit.
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-test" % "2.6.16",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"
)

mainClass in Compile := Some("testproxy.server.Main")

scalafmtVersion := "1.0.0"

(compile in Compile) := {
  (compile in Compile) dependsOn (
    scalafmt in Compile
  )
}.value

dockerRepository in Docker := Some("21re")

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

resolvers += "21re-bintray" at "http://dl.bintray.com/21re/public"

publishMavenStyle := true

bintrayOrganization := Some("21re")

bintrayRepository := "public"

bintrayCredentialsFile := {
  sys.props.get("BINTRAY_CREDENTIALS").orElse(sys.env.get("BINTRAY_CREDENTIALS")).map(new File(_)).getOrElse(baseDirectory.value / ".bintray" / "credentials")
}
