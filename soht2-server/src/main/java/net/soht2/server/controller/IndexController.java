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

  @Operation(hidden = true)
  @GetMapping({"/*"})
  public ModelAndView index(ModelMap model) {
    model.addAttribute("contextPath", contextPath);
    return new ModelAndView("index", model);
  }
}
