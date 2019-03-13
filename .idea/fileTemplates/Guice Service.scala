#if ((${PACKAGE_NAME} && ${PACKAGE_NAME} != ""))package ${PACKAGE_NAME} #end
#parse("File Header.java")
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}

@ImplementedBy(classOf[${NAME}Impl])
trait ${NAME} {

}

@Singleton
class ${NAME}Impl @Inject() (

) extends ${NAME} {

}