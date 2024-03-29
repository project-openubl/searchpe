/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.security;

import io.github.project.openubl.searchpe.dto.BasicUserDto;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.github.project.openubl.searchpe.services.BasicUserService;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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

    @Inject
    BasicUserService basicUserService;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("j_username");
        String password1 = req.getParameter("j_password1");
        String password2 = req.getParameter("j_password2");

        try {
            if (Objects.isNull(username) ||
                    Objects.isNull(password1) ||
                    Objects.isNull(password2) ||
                    !Objects.equals(password1, password2)
            ) {
                resp.sendRedirect("signup-error.html");
                return;
            }

            BasicUserDto userDto = new BasicUserDto();
            userDto.setUsername(username);
            userDto.setPassword(password1);
            userDto.setPermissions(new HashSet<>(List.of(Permission.admin)));

            BasicUserEntity userCreated = null;
            Set<ConstraintViolation<BasicUserDto>> violations = validator.validate(userDto);
            if (violations.isEmpty()) {
                try {
                    tx.begin();

                    long currentNumberOfUsers = BasicUserEntity.count();
                    if (currentNumberOfUsers == 0) {
                        userCreated = basicUserService.create(userDto);
                    }

                    tx.commit();
                } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException |
                         RollbackException | SystemException e) {
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
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
