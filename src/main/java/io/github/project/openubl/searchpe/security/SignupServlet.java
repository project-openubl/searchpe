package io.github.project.openubl.searchpe.security;

import io.github.project.openubl.searchpe.idm.BasicUserRepresentation;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.*;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@WebServlet("/j_security_signup")
public class SignupServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SignupServlet.class);

    @Inject
    UserTransaction tx;

    @Inject
    Validator validator;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("j_username");
        String password1 = req.getParameter("j_password1");
        String password2 = req.getParameter("j_password2");

        if (Objects.isNull(username) ||
                Objects.isNull(password1) ||
                Objects.isNull(password2) ||
                !Objects.equals(password1, password2)
        ) {
            resp.sendRedirect("signup-error.html");
            return;
        }

        BasicUserRepresentation userRepresentation = new BasicUserRepresentation();
        userRepresentation.setUsername(username);
        userRepresentation.setPassword(password1);
        userRepresentation.setFullName("");
        userRepresentation.setPermissions(new HashSet<>(List.of(Permission.admin)));

        BasicUserEntity userCreated = null;
        Set<ConstraintViolation<BasicUserRepresentation>> violations = validator.validate(userRepresentation);
        if (violations.isEmpty()) {
            try {
                tx.begin();

                long currentNumberOfAdmins = BasicUserEntity.find("from BasicUserEntity where permissions like '%" + Permission.admin + "'").count();
                if (currentNumberOfAdmins == 0) {
                    userCreated = BasicUserEntity.add(userRepresentation);
                }

                tx.commit();
            } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
                try {
                    tx.rollback();
                } catch (SystemException se) {
                    LOGGER.error(se);
                }
            }
        }

        if (userCreated != null) {
            resp.sendRedirect("login.html");
        } else {
            resp.sendRedirect("signup-error.html");
        }

    }
}
