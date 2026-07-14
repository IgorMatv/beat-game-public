import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import Home from './pages/Home'
import SoloConfig from './pages/SoloConfig'
import Lobby from './pages/Lobby'
import Game from './pages/Game'
import GameOver from './pages/GameOver'

function RequireSession({ children }: { children: ReactNode }) {
  const token = localStorage.getItem('playerToken')
  if (!token) return <Navigate to="/" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/solo/config" element={<SoloConfig />} />
        <Route
          path="/room/:code"
          element={
            <RequireSession>
              <Lobby />
            </RequireSession>
          }
        />
        <Route
          path="/game/:code"
          element={
            <RequireSession>
              <Game />
            </RequireSession>
          }
        />
        <Route path="/game-over" element={<GameOver />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
