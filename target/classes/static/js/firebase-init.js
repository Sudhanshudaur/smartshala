// Firebase initialization and small wrapper exposing window.Smartshala for the frontend.
// Replace the firebaseConfig below with your project's config from the Firebase console.
import { initializeApp } from 'https://www.gstatic.com/firebasejs/9.22.1/firebase-app.js';
import {
  getAuth,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signOut,
  setPersistence,
  browserLocalPersistence,
  browserSessionPersistence,
  onAuthStateChanged
} from 'https://www.gstatic.com/firebasejs/9.22.1/firebase-auth.js';
import {
  getFirestore,
  doc,
  setDoc,
  getDoc,
  collection,
  addDoc,
  query,
  orderBy,
  limit,
  where,
  serverTimestamp,
  getDocs
} from 'https://www.gstatic.com/firebasejs/9.22.1/firebase-firestore.js';
import {
  getStorage,
  ref,
  uploadBytesResumable,
  getDownloadURL
} from 'https://www.gstatic.com/firebasejs/9.22.1/firebase-storage.js';

// TODO: Replace with your Firebase project's config. You can find this in the Firebase Console -> Project settings -> SDK setup.
const firebaseConfig = {
  apiKey: "AIzaSyC8sFD2hS9ERn2IMX-YCqdexKtnuK_JLVE",
  authDomain: "smartshala-82922.firebaseapp.com",
  projectId: "smartshala-82922",
 storageBucket: "smartshala-82922.appspot.com",
  messagingSenderId: "441142128239",
  appId: "1:441142128239:web:18086fed71555bf68e279a",
  measurementId: "G-KQXRTX1563"
};

let app, auth, db, storage;

function init() {
  if (typeof window === 'undefined') return;
  if (app) return;
  app = initializeApp(firebaseConfig);
  auth = getAuth(app);
  db = getFirestore(app);
  storage = getStorage(app);

  // Keep local cached profile in localStorage for quick access
  onAuthStateChanged(auth, async (user) => {
    if (!user) {
      localStorage.removeItem('smartshala_auth');
      return;
    }
    const uDoc = await getDoc(doc(db, 'users', user.uid));
    const profile = uDoc.exists() ? { id: user.uid, ...(uDoc.data()) } : { id: user.uid, email: user.email };
    localStorage.setItem('smartshala_auth', JSON.stringify({ ...profile, isAuthenticated: true }));
  });

  // Expose a small API used by the existing frontend
  window.Smartshala = window.Smartshala || {};

  window.Smartshala.register = async ({ name, email, password, role }) => {
    const userCred = await createUserWithEmailAndPassword(auth, email, password);
    const uid = userCred.user.uid;
    await setDoc(doc(db, 'users', uid), { name, email, role, createdAt: serverTimestamp() });
    const uDoc = await getDoc(doc(db, 'users', uid));
    const profile = { id: uid, ...uDoc.data() };
    localStorage.setItem('smartshala_auth', JSON.stringify({ ...profile, isAuthenticated: true }));
    return { user: profile };
  };

  window.Smartshala.login = async ({ email, password, role, remember }) => {
    // set persistence
    await setPersistence(auth, remember ? browserLocalPersistence : browserSessionPersistence);
    const userCred = await signInWithEmailAndPassword(auth, email, password);
    const uid = userCred.user.uid;
    const uDoc = await getDoc(doc(db, 'users', uid));
    if (!uDoc.exists()) throw new Error('User profile not found');
    const profile = { id: uid, ...uDoc.data() };
    if (role && profile.role !== role) {
      await signOut(auth);
      throw new Error('Role mismatch');
    }
    localStorage.setItem('smartshala_auth', JSON.stringify({ ...profile, isAuthenticated: true }));
    return { user: profile };
  };

  window.Smartshala.logout = async () => {
    await signOut(auth);
    localStorage.removeItem('smartshala_auth');
    window.location.href = 'login.html';
  };

  window.Smartshala.isAuthenticated = () => {
    try {
      const authData = JSON.parse(localStorage.getItem('smartshala_auth') || 'null');
      return authData && authData.isAuthenticated;
    } catch (e) { return false; }
  };

  window.Smartshala.getCurrentUser = () => {
    try {
      return JSON.parse(localStorage.getItem('smartshala_auth') || 'null');
    } catch (e) { return null; }
  };

  window.Smartshala.uploadLecture = async (file, metadata = {}) => {
    const authData = window.Smartshala.getCurrentUser();
    if (!authData || !authData.id) throw new Error('Not authenticated');
    // Get duration if not provided
    let duration = metadata.duration;
    if (!duration) {
      duration = await new Promise((resolve) => {
        const v = document.createElement('video');
        v.preload = 'metadata';
        v.src = URL.createObjectURL(file);
        v.onloadedmetadata = () => { resolve(v.duration); URL.revokeObjectURL(v.src); };
      });
    }

    const timestamp = Date.now();
    const path = `lectures/${authData.id}/${timestamp}_${file.name}`;
    const storageRef = ref(storage, path);
    const uploadTask = uploadBytesResumable(storageRef, file);

    return new Promise((resolve, reject) => {
      uploadTask.on('state_changed', null, (err) => reject(err), async () => {
        const url = await getDownloadURL(storageRef);
        const lecture = {
          title: metadata.title || file.name.replace(/\.[^/.]+$/, ''),
          url,
          date: new Date().toISOString(),
          duration,
          subject: metadata.subject || 'General',
          instructor: metadata.instructor || authData.name || 'Unknown',
          uploaderId: authData.id,
          createdAt: serverTimestamp()
        };
        const docRef = await addDoc(collection(db, 'lectures'), lecture);
        resolve({ id: docRef.id, lecture });
      });
    });
  };

  window.Smartshala.listLectures = async () => {
    const q = query(collection(db, 'lectures'), orderBy('createdAt', 'desc'), limit(50));
    const snap = await getDocs(q);
    return snap.docs.map(d => ({ id: d.id, ...d.data() }));
  };

  window.Smartshala.saveNote = async (note) => {
    const authData = window.Smartshala.getCurrentUser();
    if (!authData || !authData.id) throw new Error('Not authenticated');
    const payload = { ...note, userId: authData.id, createdAt: serverTimestamp() };
    const refDoc = await addDoc(collection(db, 'notes'), payload);
    return { id: refDoc.id, ...payload };
  };

  window.Smartshala.listNotes = async () => {
    const authData = window.Smartshala.getCurrentUser();
    if (!authData || !authData.id) return [];
    const q = query(collection(db, 'notes'), where('userId', '==', authData.id), orderBy('createdAt', 'desc'));
    const snap = await getDocs(q);
    return snap.docs.map(d => ({ id: d.id, ...d.data() }));
  };
  // Summarization: call a Cloud Function or endpoint that performs AI summarization.
  // Set CLOUD_FUNCTION_SUMMARIZE to your deployed function URL in the firebaseConfig or replace below.
  const CLOUD_FUNCTION_SUMMARIZE = firebaseConfig.cloudFunctions && firebaseConfig.cloudFunctions.summarizeUrl || 'REPLACE_WITH_CLOUD_FUNCTION_URL';

  window.Smartshala.summarize = async (text) => {
    if (!text) return '';
    if (CLOUD_FUNCTION_SUMMARIZE && !CLOUD_FUNCTION_SUMMARIZE.startsWith('REPLACE')) {
      const res = await fetch(CLOUD_FUNCTION_SUMMARIZE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text })
      });
      if (!res.ok) {
        const err = await res.text();
        throw new Error('Summarize failed: ' + err);
      }
      const data = await res.json();
      return data.summary || data.result || '';
    }
    // fallback demo summarization
    const words = text.split(/\s+/);
    return words.slice(0, Math.min(words.length, 40)).join(' ') + (words.length > 40 ? '...' : '');
  };
}

// Initialize immediately so pages can use window.Smartshala
init();
