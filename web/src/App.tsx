import React, { useEffect } from "react";
import {
  Stack,
  Text,
  Link,
  FontWeights,
  IStackTokens,
  IStackStyles,
  ITextStyles,
  TextField,
  PrimaryButton,
  Image,
  IImageStyles,
  IImageProps,
  ImageFit,
  ITextFieldStyles,
  ITextFieldSubComponentStyles,
  MessageBar,
  MessageBarButton,
  MessageBarType,
  IMessageBarStyles,
} from "@fluentui/react";
import { initializeIcons } from "@fluentui/react";
import logo from "./logo_white.png";
import bg from "./bg.jpg";
import "./App.css";

const boldStyle: Partial<ITextStyles> = {
  root: { fontWeight: FontWeights.semibold },
};
const stackTokens: IStackTokens = { childrenGap: 15 };
const stackStyles: Partial<IStackStyles> = {
  root: {
    width: "100vw",
    maxWidth: "480px",
    margin: "0 auto",
    color: "#605e5c",
  },
};
const titleStyles: Partial<IStackStyles> = {
  root: {
    width: "100vw",
    maxWidth: "480px",
    margin: "0 auto",
    color: "#ffffff",
  },
};
const imageProps: Partial<IImageProps> = {
  styles: (props) => ({ root: { margin: "10px 5px" } }),
  height: 60,
};
const messageStyles: Partial<IMessageBarStyles> = {
  root: {
    position: "absolute",
  },
};

const textAreaStyles: Partial<ITextFieldStyles> = {
  root: { width: "95%" },
  subComponentStyles: { label: { root: { color: "#ffffff" } } },
};

export const App: React.FunctionComponent = () => {
  const [amount, setAmount] = React.useState("");
  const [reason, setReason] = React.useState("");
  const [showMessage, setShowMessage] = React.useState(false);
  const [messageSuccess, setMessageSuccess] = React.useState(false);
  const [vh, setVh] = React.useState(0);

  const calculateVh = (vhTotal: any) => {
    if (vhTotal > 700) {
      setVh(20);
    } else if (vhTotal > 400) {
      setVh(10);
    }
  };
  useEffect(() => {
    initializeIcons();
    calculateVh(
      Math.max(
        document.documentElement.clientHeight || 0,
        window.innerHeight || 0
      )
    );
    console.log(process.env.PUBLIC_URL);
    console.log(process.env);
  }, []);

  const onAmountChange = React.useCallback(
    (
      event: React.FormEvent<HTMLInputElement | HTMLTextAreaElement>,
      newValue?: string
    ) => {
      setAmount(newValue || "");
    },
    []
  );
  const onReasonChange = React.useCallback(
    (
      event: React.FormEvent<HTMLInputElement | HTMLTextAreaElement>,
      newValue?: string
    ) => {
      setReason(newValue || "");
    },
    []
  );
  function displayMessage(success: boolean): void {
    setShowMessage(true);
    setMessageSuccess(success);
    setTimeout(() => {
      setShowMessage(false);
      setMessageSuccess(false);
    }, 2000);
  }
  function _alertClicked(): void {
    setAmount("");
    setReason("");
    fetch(`${process.env.REACT_APP_SERVICE_URL}/api/request-payment`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: `Bearer ${process.env.REACT_APP_SERVICE_TOKEN}`,
      },
      body: JSON.stringify({ amount: amount, reason: reason }),
    })
      //.then(displayMessage)
      .then((r) => {
        if (r.status === 200) {
          displayMessage(true);
        } else {
          displayMessage(false);
        }
      })
      .catch((e) => displayMessage(false));
  }
  return (
    <div
      style={{
        height: "100vh",
        backgroundImage: `linear-gradient(rgba(0,128,128,0.5), rgba(0,0,0,0.2)), url(${bg})`,
        backgroundSize: "cover",
      }}
    >
      <Stack
        horizontalAlign="start"
        horizontal
        styles={titleStyles}
        tokens={stackTokens}
      >
        <Image {...imageProps} src={logo} />

        <h1 style={{ margin: "auto" }}>Banksy</h1>
        <div style={{ width: "60px" }}></div>
      </Stack>
      {showMessage && (
        <MessageBar
          messageBarType={
            messageSuccess ? MessageBarType.success : MessageBarType.error
          }
          styles={messageStyles}
          isMultiline={false}
        >
          {messageSuccess ? "Request submitted" : "Cannot process request"}
        </MessageBar>
      )}
      <Stack
        horizontalAlign="center"
        verticalAlign="start"
        styles={stackStyles}
        tokens={stackTokens}
      >
        <div style={{ height: vh + "vh" }}></div>
        <TextField
          label="Amount"
          styles={textAreaStyles}
          value={amount}
          onChange={onAmountChange}
          prefix="€"
          inputMode="numeric"
        />
        <TextField
          label="Reason for Payment"
          styles={textAreaStyles}
          value={reason}
          onChange={onReasonChange}
          multiline
          resizable={false}
        />
        <div style={{ marginTop: "10px" }}></div>
        <PrimaryButton
          text="Request Payment"
          styles={textAreaStyles}
          onClick={_alertClicked}
          allowDisabledFocus
        />
      </Stack>
    </div>
  );
};
