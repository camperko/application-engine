package com.netgrif.application.engine.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.ServletContext;

@Configuration
//@EnableSwagger2WebMvc
@EnableWebMvc
//@Import(BeanValidatorPluginsConfiguration.class)
public class SwaggerConfiguration {

    @Autowired
    private ServletContext servletContext;

//    @Autowired
//    private TypeResolver resolver;

    @Value("${project.version}")
    private String projectVersion;

//    @Bean
//    public Docket neaApi() {
//        return new Docket(DocumentationType.SWAGGER_2)
//                .groupName("engine")
//                .select()
//                .apis(RequestHandlerSelectors.any())
//                .paths(PathSelectors.ant("/api/**"))
//                .build()
////                .pathProvider(new RelativePathProvider(servletContext) {
////                    @Override
////                    public String getApplicationBasePath() {
////                        return "/api";
////                    }
////                })
//                .ignoredParameterTypes(
//                        File.class, URI.class, URL.class,
//                        InputStream.class, OutputStream.class, Authentication.class, Throwable.class,
//                        StackTraceElement.class, IllegalArgumentException.class, ObjectNode.class, Map.class
//                )
//                .alternateTypeRules(
//                        AlternateTypeRules.newRule(resolver.resolve(FileSystemResource.class), resolver.resolve(MultipartFile.class)),
//                        AlternateTypeRules.newRule(resolver.resolve(ResponseEntity.class, resolver.resolve(Resource.class)), resolver.resolve(MultipartFile.class))
//                )
//                .apiInfo(info())
//                .protocols(new HashSet<>(Arrays.asList("http", "https")))
//                .securitySchemes(Collections.singletonList(new BasicAuth("BasicAuth")))
//                .genericModelSubstitutes(PagedModel.class, ResponseEntity.class, List.class)
//                .useDefaultResponseMessages(false)
//                .tags(
//                        new Tag("Admin console", "Administrator console"),
//                        new Tag("Authentication", "User authentication services"),
//                        new Tag("Dashboard", "Dashboard content services"),
//                        new Tag("Elasticsearch", "Elasticsearch management services"),
//                        new Tag("Filter", "Persisted filters services"),
//                        new Tag("Group", "Group management services"),
//                        new Tag("Petri net", "Petri net management services"),
//                        new Tag("Task", "Tasks management services"),
//                        new Tag("User", "User management services"),
//                        new Tag("Workflow", "Workflow and net's cases management services")
//                )
//                ;
//    }
//
//    private ApiInfo info() {
//        return new ApiInfoBuilder()
//                .title("Netgrif Application Engine")
//                .description("Web services used in every Netgrif application engine project.")
//                .contact(new Contact("NETGRIF, s.r.o.", "https://netgrif.com", "oss@netgrif.com"))
//                .version(this.projectVersion)
//                .build();
//    }

}
