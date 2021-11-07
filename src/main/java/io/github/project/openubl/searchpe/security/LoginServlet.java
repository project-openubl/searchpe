package io.github.project.openubl.searchpe.security;

import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.vertx.ext.web.handler.sockjs.impl.StringEscapeUtils;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/login.html")
public class LoginServlet extends HttpServlet {

    @Inject
    @Location("signup.html")
    Template signupHTML;

    @Inject
    @Location("login.html")
    Template loginHTML;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long currentNumberOfAdmins = BasicUserEntity.find("from BasicUserEntity where permissions like '%" + Permission.admin + "'")
                .count();

        resp.setContentType("text/html");
        PrintWriter printWriter = resp.getWriter();
        printWriter.println(currentNumberOfAdmins == 0 ?
                signupHTML.data("hideError", true).render() :
                loginHTML.render()
        );
    }

}
