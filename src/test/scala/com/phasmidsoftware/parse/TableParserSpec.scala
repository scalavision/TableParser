/*
 * Copyright (c) 2019. Phasmid Software
 */

package com.phasmidsoftware.parse

import java.util.Date

import com.phasmidsoftware.table.{Header, Table, TableException, TableWithoutHeader}
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Codec
import scala.util.matching.Regex
import scala.util.parsing.combinator.JavaTokenParsers
import scala.util.{Failure, Success, Try}

class TableParserSpec extends FlatSpec with Matchers {

  behavior of "TableParser"

  case class IntPair(a: Int, b: Int)

  object IntPair {

    class IntPairParser extends JavaTokenParsers {
      def pair: Parser[(Int, Int)] = wholeNumber ~ wholeNumber ^^ { case x ~ y => (x.toInt, y.toInt) }
    }

    val intPairParser = new IntPairParser

    trait IntPairRowParser extends StringParser[IntPair] {
      override def parse(w: String)(header: Header): Try[IntPair] = intPairParser.parseAll(intPairParser.pair, w) match {
        case intPairParser.Success((x, y), _) => Success(IntPair(x, y))
        case _ => Failure(TableException(s"unable to parse $w"))
      }

      //noinspection NotImplementedCode
      override def parseHeader(w: String): Try[Header] = ???
    }

    implicit object IntPairRowParser extends IntPairRowParser

    trait IntPairTableParser extends StringTableParser[Table[IntPair]] {
      type Row = IntPair

      def hasHeader: Boolean = false

      def rowParser: RowParser[Row, String] = implicitly[RowParser[Row, String]]

      def builder(rows: Seq[Row]): Table[IntPair] = TableWithoutHeader(rows)
    }

    implicit object IntPairTableParser extends IntPairTableParser

  }

  it should "parse int pair" in {

    import IntPair._

    val strings: Seq[String] = Seq("1 2")
    Table.parse(strings) match {
      case Success(_) => succeed
      case Failure(x) => fail(x.getLocalizedMessage)
    }
  }

  behavior of "TableParser with StandardRowParser"

  case class DailyRaptorReport(date: LocalDate, weather: String, bw: Int, rt: Int)

  object DailyRaptorReport {
    val header: Seq[String] = Seq("date", "weather", "bw", "ri")

    object DailyRaptorReportParser extends CellParsers {

      private val raptorReportDateFormatter = DateTimeFormat.forPattern("MM/dd/yyyy")

      def parseDate(w: String): LocalDate = LocalDate.parse(w, raptorReportDateFormatter)

      implicit val dateParser: CellParser[LocalDate] = cellParser(parseDate)
      implicit val dailyRaptorReportColumnHelper: ColumnHelper[DailyRaptorReport] = columnHelper()
      implicit val dailyRaptorReportParser: CellParser[DailyRaptorReport] = cellParser4(DailyRaptorReport.apply)
    }

    import DailyRaptorReportParser._

    trait DailyRaptorReportConfig extends DefaultRowConfig {
      override val string: Regex = """[\w\/\-\ ]+""".r
      override val delimiter: Regex = """\t""".r
    }

    implicit object DailyRaptorReportConfig extends DailyRaptorReportConfig

    implicit val parser: StandardRowParser[DailyRaptorReport] = StandardRowParser[DailyRaptorReport](LineParser.apply)

    trait DailyRaptorReportTableParser extends StringTableParser[Table[DailyRaptorReport]] {
      type Row = DailyRaptorReport

      def hasHeader: Boolean = true

      def rowParser: RowParser[Row, String] = implicitly[RowParser[Row, String]]

      def builder(rows: Seq[Row]): Table[DailyRaptorReport] = TableWithoutHeader(rows)
    }

    implicit object DailyRaptorReportTableParser extends DailyRaptorReportTableParser

  }

  behavior of "RowParser.parse"

  it should "parse regex string" in {
    import DailyRaptorReport._

    val rowParser = implicitly[RowParser[DailyRaptorReport, String]]
    val firstRow = "Date\tWeather\tWnd Dir\tWnd Spd\tBV\tTV\tUV\tOS\tBE\tNH\tSS\tCH\tGO\tUA\tRS\tBW\tRT\tRL\tUB\tGE\tUE\tAK\tM\tP\tUF\tUR\tOth\tTot"
    val row = "09/16/2018\tPartly Cloudy\tSE\t6-12\t0\t0\t0\t4\t19\t3\t30\t2\t0\t0\t2\t3308\t5\t0\t0\t0\t0\t27\t8\t1\t0\t1\t0\t3410"
    val Success(header) = rowParser.parseHeader(firstRow)

    val hawkCount: Try[DailyRaptorReport] = parser.parse(row)(header)
    hawkCount should matchPattern { case Success(DailyRaptorReport(_, "Partly Cloudy", 3308, 5)) => }
  }

  behavior of "Table.parse"

  it should "parse raptors from raptors.csv" in {
    import DailyRaptorReport._

    val x: Try[Table[DailyRaptorReport]] = for (r <- Table.parse(classOf[TableParserSpec].getResource("/raptors.csv"))) yield r
    x should matchPattern { case Success(TableWithoutHeader(_)) => }
    x.get.rows.size shouldBe 13
    //noinspection ScalaDeprecation
    x.get.rows.head shouldBe DailyRaptorReport(LocalDate.fromDateFields(new Date(118, 8, 12)), "Dense Fog/Light Rain", 0, 0)
  }

  it should "parse raptors from Seq[String]" in {
    import DailyRaptorReport._

    val raw = Seq("Date\tWeather\tWnd Dir\tWnd Spd\tBV\tTV\tUV\tOS\tBE\tNH\tSS\tCH\tGO\tUA\tRS\tBW\tRT\tRL\tUB\tGE\tUE\tAK\tM\tP\tUF\tUR\tOth\tTot",
      "09/16/2018\tPartly Cloudy\tSE\t6-12\t0\t0\t0\t4\t19\t3\t30\t2\t0\t0\t2\t3308\t5\t0\t0\t0\t0\t27\t8\t1\t0\t1\t0\t3410",
      "09/19/2018\tOvercast/Mostly cloudy/Partly cloudy/Clear\tNW\t4-7\t0\t0\t0\t47\t12\t0\t84\t10\t0\t0\t1\t821\t4\t0\t1\t0\t0\t27\t4\t1\t0\t2\t0\t1014")
    val x: Try[Table[DailyRaptorReport]] = for (r <- Table.parse(raw)) yield r
    x should matchPattern { case Success(TableWithoutHeader(_)) => }
    x.get.rows.size shouldBe 2
    //noinspection ScalaDeprecation
    x.get.rows.head shouldBe DailyRaptorReport(LocalDate.fromDateFields(new Date(118, 8, 16)), "Partly Cloudy", 3308, 5)

  }

  it should "parse empty sequence" in {
    import DailyRaptorReport._

    val raw = Seq("Date\tWeather\tWnd Dir\tWnd Spd\tBV\tTV\tUV\tOS\tBE\tNH\tSS\tCH\tGO\tUA\tRS\tBW\tRT\tRL\tUB\tGE\tUE\tAK\tM\tP\tUF\tUR\tOth\tTot",
      "")
    val x = for (r <- Table.parse(raw)) yield r
    x should matchPattern { case Failure(_) => }
  }

  object DailyRaptorReportSeq {
    val header: Seq[String] = Seq("date", "weather", "bw", "ri")

    object DailyRaptorReportParser extends CellParsers {

      private val raptorReportDateFormatter = DateTimeFormat.forPattern("MM/dd/yyyy")

      def parseDate(w: String): LocalDate = LocalDate.parse(w, raptorReportDateFormatter)

      implicit val dateParser: CellParser[LocalDate] = cellParser(parseDate)
      implicit val dailyRaptorReportColumnHelper: ColumnHelper[DailyRaptorReport] = columnHelper()
      implicit val dailyRaptorReportParser: CellParser[DailyRaptorReport] = cellParser4(DailyRaptorReport.apply)
    }

    import DailyRaptorReportParser._

    trait DailyRaptorReportConfig extends DefaultRowConfig {
      override val string: Regex = """[\w\/\-\ ]+""".r
      override val delimiter: Regex = """\t""".r
    }

    implicit object DailyRaptorReportConfig extends DailyRaptorReportConfig

    implicit val parser: StandardStringsParser[DailyRaptorReport] = StandardStringsParser[DailyRaptorReport]()

    trait DailyRaptorReportStringsTableParser extends StringsTableParser[Table[DailyRaptorReport]] {
      type Row = DailyRaptorReport

      def hasHeader: Boolean = true

      def rowParser: RowParser[Row, Seq[String]] = implicitly[RowParser[Row, Seq[String]]]

      def builder(rows: Seq[Row]): Table[DailyRaptorReport] = TableWithoutHeader(rows)
    }

    implicit object DailyRaptorReportStringsTableParser extends DailyRaptorReportStringsTableParser

  }

  it should "parse raptors from Seq[Seq[String]]" in {
    import DailyRaptorReportSeq._

    val raw = Seq(Seq("Date", "Weather", "Wnd Dir", "Wnd Spd", "BV", "TV", "UV", "OS", "BE", "NH", "SS", "CH", "GO", "UA", "RS", "BW", "RT", "RL", "UB", "GE", "UE", "AK", "M", "P", "UF", "UR", "Oth", "Tot"),
      Seq("09/16/2018", "Partly Cloudy", "SE", "6-12", "0", "0", "0", "4", "19", "3", "30", "2", "0", "0", "2", "3308", "5", "0", "0", "0", "0", "27", "8", "1", "0", "1", "0", "3410"),
      Seq("09/19/2018", "Overcast/Mostly cloudy/Partly cloudy/Clear", "NW", "4-7", "0", "0", "0", "47", "12", "0", "84", "10", "0", "0", "1", "821", "4", "0", "1", "0", "0", "27", "4", "1", "0", "2", "0", "1014"))
    val x: Try[Table[DailyRaptorReport]] = for (r <- Table.parseSequence(raw)) yield r
    x should matchPattern { case Success(TableWithoutHeader(_)) => }
    x.get.rows.size shouldBe 2
    //noinspection ScalaDeprecation
    x.get.rows.head shouldBe DailyRaptorReport(LocalDate.fromDateFields(new Date(118, 8, 16)), "Partly Cloudy", 3308, 5)

  }

  behavior of "StringsParser"

  behavior of "Table"

  case class Question(question_ID: String, question: String, answer: Option[String], possible_points: Int, auto_score: Option[Double], manual_score: Option[Double])

  case class Submission(username: String, last_name: String, first_name: String, questions: Seq[Question])

  object Submissions extends CellParsers {

    def baseColumnNameMapper(w: String): String = w.replaceAll("(_)", " ")

    implicit val submissionColumnHelper: ColumnHelper[Submission] = columnHelper(baseColumnNameMapper _)
    implicit val questionColumnHelper: ColumnHelper[Question] = columnHelper(baseColumnNameMapper _, Some("$c $x"))
    implicit val optionalAnswerParser: CellParser[Option[String]] = cellParserOption
    implicit val questionParser: CellParser[Question] = cellParser6(Question)
    implicit val questionsParser: CellParser[Seq[Question]] = cellParserRepetition[Question]()
    implicit val submissionParser: CellParser[Submission] = cellParser4(Submission)
    implicit val parser: StandardStringsParser[Submission] = StandardStringsParser[Submission]()

    implicit object SubmissionTableParser extends StringsTableParser[Table[Submission]] {
      type Row = Submission

      def hasHeader: Boolean = true

      override def forgiving: Boolean = false

      def rowParser: RowParser[Row, Seq[String]] = implicitly[RowParser[Row, Seq[String]]]

      def builder(rows: Seq[Row]): Table[Submission] = TableWithoutHeader(rows)
    }

  }

  it should "parse Submission" in {

    val rows: Seq[Seq[String]] = Seq(
      Seq("Username", "Last Name", "First Name", "Question ID 1", "Question 1", "Answer 1", "Possible Points 1", "Auto Score 1", "Manual Score 1"),
      Seq("001234567s", "Mr.", "Nobody", "Question ID 1", "The following are all good reasons to learn Scala -- except for one.", "Scala is the only functional language available on the Java Virtual Machine", "4", "4", "")
    )

    import Submissions._
    val qty: Try[Table[Submission]] = Table.parseSequence(rows)
    qty should matchPattern { case Success(_) => }
    qty.get.size shouldBe 1
    println(qty.get.head)
  }


  behavior of "submissions from file"

  object Submissions1 extends CellParsers {

    def baseColumnNameMapper(w: String): String = w.replaceAll("(_)", " ")

    implicit val submissionColumnHelper: ColumnHelper[Submission] = columnHelper(baseColumnNameMapper _)
    implicit val questionColumnHelper: ColumnHelper[Question] = columnHelper(baseColumnNameMapper _, Some("$c $x"))
    implicit val optionalAnswerParser: CellParser[Option[String]] = cellParserOption
    implicit val questionParser: CellParser[Question] = cellParser6(Question)
    implicit val questionsParser: CellParser[Seq[Question]] = cellParserRepetition[Question]()
    implicit val submissionParser: CellParser[Submission] = cellParser4(Submission)

    implicit object SubmissionConfig extends DefaultRowConfig {
      override val string: Regex = """[^\t]*""".r
      override val delimiter: Regex = """\t""".r
    }

    implicit val parser: StandardRowParser[Submission] = StandardRowParser[Submission]

    implicit object SubmissionTableParser extends StringTableParser[Table[Submission]] {
      type Row = Submission

      def hasHeader: Boolean = true

      override def forgiving: Boolean = true

      def rowParser: RowParser[Row, String] = implicitly[RowParser[Row, String]]

      def builder(rows: Seq[Row]): Table[Submission] = TableWithoutHeader(rows)
    }

  }

  it should "parse sample.csv with Submission1" in {
    import Submissions1._
    implicit val codec: Codec = Codec("UTF-16")
    val qty: Try[Table[Submission]] = Table.parseResource("submissions.csv", classOf[TableParserSpec])
    qty should matchPattern { case Success(_) => }
    qty.get.size shouldBe 1
  }


}
