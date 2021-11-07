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

@WebServlet("/auth-switch")
public class AuthSwitchServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long currentNumberOfAdmins = BasicUserEntity.find("from BasicUserEntity where permissions like '%" + Permission.admin + "'").count();
        if (currentNumberOfAdmins > 0) {
            resp.sendRedirect("login.html");
        } else {
            resp.sendRedirect("signup.html");
        }
    }

}
