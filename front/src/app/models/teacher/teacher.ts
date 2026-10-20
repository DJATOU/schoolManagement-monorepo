import { Group } from '../group/group';

export interface Teacher {
  id?: number;
  firstName: string;
  lastName: string;
  gender: string;
  email: string;
  phoneNumber: string;
  /** Jour du calendrier `yyyy-MM-dd` (ni heure ni fuseau) ; lire et écrire via `shared/iso-day`. */
  dateOfBirth: string | null;
  placeOfBirth: string;
  address?: string;            // Optional based on your usage
  city?: string;               // Optional
  photo?: string;              // Optional
  nationality?: string;        // Optional
  communicationPreference?: string; // Optional
  specialization?: string;     // Optional
  yearsOfExperience?: number;  // Optional, could be null if not set
  groups: Group[];             // Array to handle multiple groups, matching the backend "Set"
}
