import React from "react";
import { createRoot } from "react-dom/client";
import "./index.scss";
import App from "./App";
import reportWebVitals from "./reportWebVitals";

import { QueryClientProvider, QueryClient, QueryCache } from "react-query";
import { ReactQueryDevtools } from "react-query/devtools";

import { Provider } from "react-redux";
import configureStore from "./store";

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

createRoot(document.getElementById("root") as any).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <Provider store={configureStore()}>
        <App />
      </Provider>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
