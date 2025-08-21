package net.soht2.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class IndexController {

  @Value("${server.servlet.context-path:/}")
  private String contextPath;

  @Value("${soht2.server.open-api-server-url:./}${springdoc.swagger-ui.path:}")
  private String swaggerUrl;

  @Operation(hidden = true)
  @GetMapping({"/*"})
  public ModelAndView index(ModelMap model) {
    model.addAttribute("contextPath", contextPath);
    model.addAttribute("swaggerUrl", swaggerUrl);
    return new ModelAndView("index", model);
  }
}
