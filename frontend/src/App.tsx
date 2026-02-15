import React from 'react';
import { ReactKeycloakProvider } from '@react-keycloak/web';
import Keycloak, { KeycloakConfig } from 'keycloak-js';
import ReportPage from './components/ReportPage';

const keycloakConfig: KeycloakConfig = {
    url: process.env.REACT_APP_KEYCLOAK_URL,
    realm: process.env.REACT_APP_KEYCLOAK_REALM || "",
    clientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID || ""
};

const keycloak = new Keycloak(keycloakConfig);

keycloak.onReady = (authenticated: boolean) => console.log('Keycloak ready:', authenticated);
keycloak.onAuthError = (error: any) => console.error('Keycloak auth error:', error);
keycloak.onAuthSuccess = () => console.log('Keycloak auth success');

const App: React.FC = () => {
    const eventLogger = (event: unknown, error?: unknown) => {
        console.log('Keycloak event:', event, error);
    };

    return (
        <ReactKeycloakProvider
            authClient={keycloak}
            initOptions={{
                onLoad: 'check-sso',
                pkceMethod: 'S256',  // <--- Включаем PKCE с SHA-256
                flow: 'standard',
                checkLoginIframe: false,
            }}
            onEvent={eventLogger}
            LoadingComponent={<div style={{padding: '20px'}}>Initializing Keycloak...</div>}
        >
            <div className="App">
                <ReportPage />
            </div>
        </ReactKeycloakProvider>
    );
};

export default App;
