import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const ReportPage: React.FC = () => {
    const [authenticated, setAuthenticated] = useState<boolean | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();

    // Проверка сессии на бэкенде
    useEffect(() => {
        fetch(`${process.env.REACT_APP_AUTH_URL}/status`, {
            credentials: 'include',
        })
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(data => setAuthenticated(data.authenticated))
            .catch(() => setAuthenticated(false));
    }, []);

    const handleLogin = () => {
        // Генерируем code_verifier (совместимо с ES5)
        const generateCodeVerifier = () => {
            const array = new Uint8Array(32);
            window.crypto.getRandomValues(array);
            // Используем apply вместо spread
            const binary = String.fromCharCode.apply(null, Array.from(array));
            return btoa(binary)
                .replace(/\+/g, '-')
                .replace(/\//g, '_')
                .replace(/=/g, '');
        };

        // Генерируем code_challenge
        const generateCodeChallenge = async (verifier: string) => {
            const encoder = new TextEncoder();
            const data = encoder.encode(verifier);
            const digest = await window.crypto.subtle.digest('SHA-256', data);
            const binary = String.fromCharCode.apply(null, Array.from(new Uint8Array(digest)));
            return btoa(binary)
                .replace(/\+/g, '-')
                .replace(/\//g, '_')
                .replace(/=/g, '');
        };

        (async () => {
            const verifier = generateCodeVerifier();
            const challenge = await generateCodeChallenge(verifier);
            sessionStorage.setItem('pkce_verifier', verifier);

            const params = new URLSearchParams({
                response_type: 'code',
                client_id: process.env.REACT_APP_KEYCLOAK_CLIENT_ID!,
                redirect_uri: `${window.location.origin}/callback`,
                code_challenge: challenge,
                code_challenge_method: 'S256',
                scope: 'openid',
            });

            const authUrl = `${process.env.REACT_APP_KEYCLOAK_URL}/realms/${process.env.REACT_APP_KEYCLOAK_REALM}/protocol/openid-connect/auth?${params}`;
            window.location.href = authUrl;
        })();
    };

    const handleLogout = async () => {
        await fetch(`${process.env.REACT_APP_AUTH_URL}/logout`, {
            method: 'POST',
            credentials: 'include',
        });
        setAuthenticated(false);
    };

    const downloadReport = async () => {
        setLoading(true);
        setError(null);
        try {
            const res = await fetch(`${process.env.REACT_APP_AUTH_URL}/api/report`, {
                credentials: 'include',
            });
            if (!res.ok) throw new Error('Failed to download');
            const data = await res.json();
            console.log('Report:', data);
            alert('Report downloaded (check console)');
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    if (authenticated === null) return <div>Loading...</div>;

    if (!authenticated) {
        return (
            <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
                <button
                    onClick={handleLogin}
                    className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
                >
                    Login
                </button>
                {error && <div className="mt-4 text-red-600">{error}</div>}
            </div>
        );
    }

    return (
        <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
            <div className="p-8 bg-white rounded-lg shadow-md">
                <h1 className="text-2xl font-bold mb-6">Usage Reports</h1>
                <button
                    onClick={downloadReport}
                    disabled={loading}
                    className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:opacity-50"
                >
                    {loading ? 'Generating...' : 'Download Report'}
                </button>
                <button
                    onClick={handleLogout}
                    className="ml-4 px-4 py-2 bg-gray-500 text-white rounded hover:bg-gray-600"
                >
                    Logout
                </button>
                {error && <div className="mt-4 p-4 bg-red-100 text-red-700 rounded">{error}</div>}
            </div>
        </div>
    );
};

export default ReportPage;