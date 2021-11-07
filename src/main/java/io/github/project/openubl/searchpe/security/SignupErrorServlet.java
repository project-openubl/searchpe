package io.github.project.openubl.searchpe.security;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/signup-error.html")
public class SignupErrorServlet extends HttpServlet {

    @Inject
    @Location("signup-error.html")
    Template signupErrorHTML;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        PrintWriter printWriter = resp.getWriter();
        printWriter.println(signupErrorHTML.render());
    }

}
