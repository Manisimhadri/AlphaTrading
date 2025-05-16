/* eslint-disable no-unused-vars */
import api from "@/Api/api";
import {
  CHAT_BOT_FAILURE,
  CHAT_BOT_REQUEST,
  CHAT_BOT_SUCCESS,
} from "./ActionTypes";

export const sendMessage = ({ prompt, jwt }) => async (dispatch) => {
  // Dispatch request action
  dispatch({
    type: CHAT_BOT_REQUEST,
    payload: { prompt, role: "user" },
  });

  try {
    const { data } = await api.post(
      "/api/chat/bot/coin",
      { prompt },
      {
        headers: {
          Authorization: `Bearer ${jwt}`,
        },
      }
    );

    // Dispatch success with response
    dispatch({
      type: CHAT_BOT_SUCCESS,
      payload: { ans: data.message, role: "model" },
    });

    console.log("Success Response:", data);
  } catch (error) {
    console.error("Error in sendMessage:", error);

    // Error handling
    const errorMessage =
      error.response?.data?.message ||
      error.message ||
      "An unknown error occurred.";

    dispatch({ type: CHAT_BOT_FAILURE, payload: errorMessage });

    alert(`Chatbot Error: ${errorMessage}`);
  }
};
