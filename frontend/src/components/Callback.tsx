import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const Callback: React.FC = () => {
    const navigate = useNavigate();

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const code = params.get('code');
        const verifier = sessionStorage.getItem('pkce_verifier');

        if (!code || !verifier) {
            navigate('/');
            return;
        }

        // Отправляем код и верификатор на бэкенд
        fetch(`${process.env.REACT_APP_AUTH_URL}/auth/callback`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code, codeVerifier: verifier }),
            credentials: 'include',
        })
            .then(res => {
                if (res.ok) {
                    sessionStorage.removeItem('pkce_verifier');
                    navigate('/');
                } else {
                    throw new Error('Auth failed');
                }
            })
            .catch(err => {
                console.error(err);
                navigate('/');
            });
    }, [navigate]);

    return <div>Processing login...</div>;
};

export default Callback;