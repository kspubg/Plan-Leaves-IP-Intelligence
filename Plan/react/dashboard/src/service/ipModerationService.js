import axios from "axios";
import {baseAddress, doGetRequest, doSomeRequest, standard200option} from "./backendConfiguration";

export const fetchIpModeration = () => doGetRequest("/v1/ipModeration");
export const mutateIpModeration = (csrf, body) => doSomeRequest("/v1/ipModeration", [standard200option],
    () => axios.post(baseAddress + "/v1/ipModeration", body,
        {headers: {"X-Plan-CSRF": csrf, "Content-Type": "application/json"}}));
