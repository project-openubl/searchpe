import {lazy, Suspense} from "react";
import {Navigate, Route, Routes} from "react-router-dom";

import {SimplePlaceholder} from "@project-openubl/lib-ui";

import {ProtectedRoute} from "ProtectedRoute";
import {Permission} from "Constants";
import {Paths} from "Paths";

const ConsultaRuc = lazy(() => import("./pages/consulta-ruc"));
const Contribuyentes = lazy(() => import("./pages/contribuyentes"));
const Versions = lazy(() => import("./pages/versions"));
const Users = lazy(() => import("./pages/users"));
const Profile = lazy(() => import("./pages/profile"));

export const AppRoutes = () => {
    const routes = [
        {
            Component: ConsultaRuc,
            path: Paths.consultaRuc,
            hasAny: [Permission.admin, Permission.search],
        },
        {
            Component: Contribuyentes,
            path: Paths.contribuyentes,
            hasAny: [Permission.admin, Permission.search],
        },
        {
            Component: Versions,
            path: Paths.versiones,
            hasAny: [Permission.admin, Permission.version_write],
        },
        {
            Component: Users,
            path: Paths.users,
            hasAny: [Permission.admin, Permission.user_write],
        },
        {
            Component: Profile,
            path: Paths.profile,
            hasDescendant: true,
        },
    ];

    return (
        <Suspense fallback={<SimplePlaceholder/>}>
            <Routes>
                {routes.map(({path, hasAny, hasDescendant, Component}, index) => (
                    <Route
                        key={index}
                        path={hasDescendant ? `${path}/*` : path}
                        element={
                            hasAny ? (
                                <ProtectedRoute hasAny={hasAny}>
                                    <Component/>
                                </ProtectedRoute>
                            ) : (
                                <Component/>
                            )
                        }
                    />
                ))}
                <Route
                    path="*"
                    element={
                        <Navigate
                            to={
                                Paths.consultaRuc
                            }
                        />
                    }
                />
            </Routes>
        </Suspense>
    );
};
