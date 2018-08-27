package system

import services.timing.TimingService.Category

object TimingCategories {
  object Http extends Category(id = "HTTP")
  object Tabula extends Category(id = "Tabula", inherits = Seq(Http))
  object Db extends Category(id = "DB")
}
