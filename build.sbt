lazy val readme = scalatex.ScalatexReadme(
  projectId = "readme",
  wd = file(""),
  url = "https://github.com/lihaoyi/fastparse/tree/master",
  source = "Readme",
  autoResources = List("out.js", "JProfiler.png")
).settings(
  (Compile / resources) += baseDirectory.value/".."/"out"/"demo"/"fullOpt"/"dest"/"out.js",
  scalaVersion := Scala212
)

val Scala212 = "2.12.8"
val Scala213 = "2.13.15"

lazy val root = project in file(".")

ThisBuild / scalaVersion := Scala213

lazy val commonTestSettings = libraryDependencies ++= Seq(
  "com.lihaoyi" %% "utest" % "0.7.0" cross CrossVersion.for3Use2_13,
  "org.scala-lang" % "scala-reflect" % Scala213,
  "org.scala-lang" % "scala-compiler" % Scala213,
)

lazy val fastparse = (project in file("fastparse"))
  .settings(
    name := "fastparse",
    fork := true,

    libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.1.7" cross CrossVersion.for3Use2_13,

    Compile / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value
      Seq(base / "src", base / "src-jvm")
    },

    Test / unmanagedSourceDirectories += baseDirectory.value / "test" / "src",

    Compile / sourceGenerators += Def.task {
      val dir = (Compile / sourceManaged).value
      val file = dir/"fastparse"/"SequencerGen.scala"
      // Only go up to 21, because adding the last element makes it 22
      val tuples = (2 to 21).map{ i =>
        val ts = (1 to i) map ("T" + _)
        val chunks = (1 to i) map { n =>
          s"t._$n"
        }
        val tsD = (ts :+ "D").mkString(",")
        s"""
          implicit def Sequencer$i[$tsD]: Sequencer[(${ts.mkString(", ")}), D, ($tsD)] =
            Sequencer0((t, d) => (${chunks.mkString(", ")}, d))
          """
      }
      val output = s"""
        package fastparse
        trait SequencerGen[Sequencer[_, _, _]] extends LowestPriSequencer[Sequencer]{
          protected[this] def Sequencer0[A, B, C](f: (A, B) => C): Sequencer[A, B, C]
          ${tuples.mkString("\n")}
        }
        trait LowestPriSequencer[Sequencer[_, _, _]]{
          protected[this] def Sequencer0[A, B, C](f: (A, B) => C): Sequencer[A, B, C]
          implicit def Sequencer1[T1, T2]: Sequencer[T1, T2, (T1, T2)] = Sequencer0{case (t1, t2) => (t1, t2)}
        }
        """.stripMargin
      IO.write(file, output)
      Seq(file)
    }
  )
  .settings(commonTestSettings)
  .settings(dottySettings)

lazy val cssparse = (project in file("cssparse"))
  .settings(
    name := "cssparse",
    fork := true,
    libraryDependencies += "net.sourceforge.cssparser" % "cssparser" % "0.9.18",
    Compile / scalaSource := baseDirectory.value / "src",
    Test / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value / "test"
      Seq(base / "src", base / "src-jvm")
    }
  )
  .settings(commonTestSettings)
  .settings(dottySettings)
  .dependsOn(fastparse)

lazy val scalaparse = (project in file("scalaparse"))
  .settings(
    name := "scalaparse",
    fork := true,
    Compile / scalaSource := baseDirectory.value / "src",
    Test / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value / "test"
      Seq(base / "src", base / "src-jvm")
    },
    Test / unmanagedResourceDirectories += baseDirectory.value / "test" / "resources"
  )
  .settings(commonTestSettings)
  .settings(dottySettings)
  .dependsOn(fastparse)

lazy val pythonparse = (project in file("pythonparse"))
  .settings(
    name := "pythonparse",
    fork := true,
    Compile / scalaSource := baseDirectory.value / "src",
    Test / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value / "test"
      Seq(base / "src", base / "src-jvm")
    },
    Test / unmanagedResourceDirectories += baseDirectory.value / "test" / "resources"
  )
  .settings(commonTestSettings)
  .settings(dottySettings)
  .dependsOn(fastparse)

lazy val `perftests-common` =
  Compile / unmanagedResourceDirectories += (LocalRootProject / baseDirectory).value / "perftests" / "resources"

lazy val `perftests-bench2` = (project in file("perftests/bench2"))
  .settings(
    Compile / scalaSource := baseDirectory.value / "src"
  )
  .settings(`perftests-common`)
  .settings(commonTestSettings)
  .settings(dottySettings)
  .dependsOn(
    fastparse % "compile->compile;compile->test",
    scalaparse % "compile->compile;compile->test",
    cssparse,
    pythonparse
  )

lazy val `perftests-compare` = (project in file("perftests/compare"))
  .settings(
    Compile / scalaSource := baseDirectory.value / "src",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-ast" % "3.6.7",
      "org.json4s" %% "json4s-native" % "3.6.7",
      "org.json4s" %% "json4s-jackson" % "3.6.7",
      "io.circe" %% "circe-parser" % "0.12.0-RC3",
      "io.argonaut" %% "argonaut" % "6.2.3",
      "com.typesafe.play" %% "play-json" % "2.8.0-M5",
      "com.lihaoyi" %% "ujson" % "0.7.5",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
    ).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= Seq(
      "org.python" % "jython" % "2.7.1b3",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9.3",
    ),
  )
  .settings(`perftests-common`)
  .settings(commonTestSettings)
  .settings(dottySettings)
  .dependsOn(
    fastparse % "compile->compile;compile->test",
    scalaparse % "compile->compile;compile->test",
    cssparse,
    pythonparse
  )

lazy val `dotty-community-build` = (project in file(".dotty-community-build"))
  .aggregate(fastparse, cssparse, scalaparse, pythonparse, `perftests-bench2`, `perftests-compare`)
  .settings(dottySettings)

lazy val dottySettings = List(
  scalacOptions ++= List("-language:implicitConversions", "-Xignore-scala2-macros", "-source:3.0-migration")
)
