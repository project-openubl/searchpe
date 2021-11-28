import React from "react";
import ReactDOM from "react-dom";
import "./index.scss";
import App from "./App";
import reportWebVitals from "./reportWebVitals";

import { QueryClientProvider, QueryClient, QueryCache } from "react-query";
import { ReactQueryDevtools } from "react-query/devtools";

import { Provider } from "react-redux";
import configureStore from "./store";

import KeycloakWrapper from "./keycloak";
import { isBasicAuthEnabled, isOidcAuthEnabled } from "Constants";

const queryCache = new QueryCache();
const queryClient = new QueryClient({
  queryCache,
  defaultOptions: {
    queries: {
      refetchOnMount: false,
      refetchOnWindowFocus: false,
    },
  },
});

const BasicApp = (
  <Provider store={configureStore()}>
    <App />
  </Provider>
);
const OidcApp = <KeycloakWrapper>{BasicApp}</KeycloakWrapper>;

let SearchpeApp;
if (isBasicAuthEnabled()) {
  SearchpeApp = BasicApp;
} else if (isOidcAuthEnabled()) {
  SearchpeApp = OidcApp;
} else {
  throw new Error("Couldn't define auth method");
}

ReactDOM.render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      {SearchpeApp}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </React.StrictMode>,
  document.getElementById("root")
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
