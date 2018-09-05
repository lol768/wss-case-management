package domain

import org.scalatestplus.play.PlaySpec

class IssueKeyTest extends PlaySpec {

  "IssueKey" should {
    "have a sensible toString" in {
      IssueKey(IssueKeyType.Case, 8).string mustBe "CAS-008"
      IssueKey(IssueKeyType.Enquiry, 8).string mustBe "ENQ-008"
      IssueKey(IssueKeyType.Enquiry, 100).string mustBe "ENQ-100"
      IssueKey(IssueKeyType.Case, 12345).string mustBe "CAS-12345"
    }

    "parse values sensibly" in {
      IssueKey("CAS-008") mustBe IssueKey(IssueKeyType.Case, 8)
      IssueKey("CAS008") mustBe IssueKey(IssueKeyType.Case, 8)
      IssueKey("CAS08") mustBe IssueKey(IssueKeyType.Case, 8)
      IssueKey("CAS8") mustBe IssueKey(IssueKeyType.Case, 8)
      IssueKey("c8") mustBe IssueKey(IssueKeyType.Case, 8)
      IssueKey("E12345") mustBe IssueKey(IssueKeyType.Enquiry, 12345)
    }

    "throw exceptions for invalid values" in {
      an[IllegalArgumentException] mustBe thrownBy { IssueKey("123") }
      a[NoSuchElementException] mustBe thrownBy { IssueKey("X8") }
    }
  }

}
