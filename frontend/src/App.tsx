import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import ReportPage from './components/ReportPage';
import Callback from './components/Callback';

const App: React.FC = () => {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<ReportPage />} />
                <Route path="/callback" element={<Callback />} />
            </Routes>
        </BrowserRouter>
    );
};

export default App;