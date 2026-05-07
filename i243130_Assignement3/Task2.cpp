

#include <iostream>
using namespace std;
 
#define maxp 20
#define maxr 20
 
struct systemstate {
    int np, nr;
    int maxd[maxp][maxr];
    int alloc[maxp][maxr];
    int need[maxp][maxr];
    int avail[maxr];
};
 
void computeneed(struct systemstate &s) {
    for (int count = 0; count < s.np; count++)
        for (int count2 = 0; count2 < s.nr; count2++)
            s.need[count][count2] = s.maxd[count][count2] - s.alloc[count][count2];
}
 
int safetycheck(struct systemstate &s, int seq[], int &len) {
    int work[maxr];
    int done[maxp] = {0};
    len = 0;
 
    for (int count2 = 0; count2 < s.nr; count2++)
        work[count2] = s.avail[count2];
 
    int found;
    do {
        found = 0;
        for (int count = 0; count < s.np; count++) {
            if (done[count]) continue;
            int ok = 1;
            for (int count2 = 0; count2 < s.nr; count2++)
                if (s.need[count][count2] > work[count2]) { ok = 0; break; }
            if (ok) {
                for (int count2 = 0; count2 < s.nr; count2++)
                    work[count2] += s.alloc[count][count2];
                done[count] = 1;
                seq[len++] = count;
                found = 1;
            }
        }
    } while (found);
 
    return len == s.np;
}
 
void printsequence(int seq[], int len) {
    cout << "safe sequence: ";
    for (int count = 0; count < len; count++) {
        cout << "p" << seq[count];
        if (count + 1 < len) cout << " -> ";
    }
    cout << "\n";
}
 
void printstate(struct systemstate &s) {
    cout << "available: ";
    for (int count2 = 0; count2 < s.nr; count2++) cout << s.avail[count2] << " ";
    cout << "\n";
    cout << "process  alloc  need\n";
    for (int count = 0; count < s.np; count++) {
        cout << "p" << count << "       ";
        for (int count2 = 0; count2 < s.nr; count2++) cout << s.alloc[count][count2] << " ";
        cout << "  ";
        for (int count2 = 0; count2 < s.nr; count2++) cout << s.need[count][count2] << " ";
        cout << "\n";
    }
}
 
void handlerequest(struct systemstate &s, int pid, int req[]) {
    cout << "request by p" << pid << " [";
    for (int count2 = 0; count2 < s.nr; count2++) {
        cout << req[count2];
        if (count2 + 1 < s.nr) cout << ",";
    }
    cout << "] -- ";
 
    for (int count2 = 0; count2 < s.nr; count2++) {
        if (req[count2] > s.need[pid][count2]) {
            cout << "denied\n";
            cout << "reason: request exceeds declared need for p" << pid << ".\n";
            return;
        }
    }
 
    for (int count2 = 0; count2 < s.nr; count2++) {
        if (req[count2] > s.avail[count2]) {
            cout << "denied\n";
            cout << "reason: insufficient available resources. p" << pid << " must wait.\n";
            return;
        }
    }
 
    for (int count2 = 0; count2 < s.nr; count2++) {
        s.avail[count2]      -= req[count2];
        s.alloc[pid][count2] += req[count2];
        s.need[pid][count2]  -= req[count2];
    }
 
    int seq[maxp], len;
    if (safetycheck(s, seq, len)) {
        cout << "granted\n";
        printsequence(seq, len);
    } else {
        for (int count2 = 0; count2 < s.nr; count2++) {
            s.avail[count2]      += req[count2];
            s.alloc[pid][count2] -= req[count2];
            s.need[pid][count2]  += req[count2];
        }
        cout << "denied\n";
        cout << "reason: resulting state is unsafe.\n";
        cout << "state rolled back. system remains safe.\n";
    }
}
 
void loadinput(struct systemstate &s) {
    cout << "enter number of processes: ";
    cin >> s.np;
    if (s.np <= 0 || s.np > maxp) { cout << "invalid.\n"; exit(1); }
 
    cout << "enter number of resource types: ";
    cin >> s.nr;
    if (s.nr <= 0 || s.nr > maxr) { cout << "invalid.\n"; exit(1); }
 
    cout << "enter max matrix (" << s.np << " rows, " << s.nr << " cols):\n";
    for (int count = 0; count < s.np; count++) {
        cout << "p" << count << ": ";
        for (int count2 = 0; count2 < s.nr; count2++) {
            cin >> s.maxd[count][count2];
            if (s.maxd[count][count2] < 0) { cout << "values must be >= 0.\n"; exit(1); }
        }
    }
 
    cout << "enter allocation matrix (" << s.np << " rows, " << s.nr << " cols):\n";
    for (int count = 0; count < s.np; count++) {
        cout << "p" << count << ": ";
        for (int count2 = 0; count2 < s.nr; count2++) {
            cin >> s.alloc[count][count2];
            if (s.alloc[count][count2] < 0 || s.alloc[count][count2] > s.maxd[count][count2]) {
                cout << "allocation invalid for p" << count << " resource " << count2 << ".\n";
                exit(1);
            }
        }
    }
 
    cout << "enter available vector (" << s.nr << " values): ";
    for (int count2 = 0; count2 < s.nr; count2++) {
        cin >> s.avail[count2];
        if (s.avail[count2] < 0) { cout << "available must be >= 0.\n"; exit(1); }
    }
}
 
void loadsim(struct systemstate &s,
             int np, int nr,
             int maxd[][maxr], int alloc[][maxr], int avail[]) {
    s.np = np;
    s.nr = nr;
    for (int i = 0; i < np; i++)
        for (int j = 0; j < nr; j++) {
            s.maxd[i][j]  = maxd[i][j];
            s.alloc[i][j] = alloc[i][j];
        }
    for (int j = 0; j < nr; j++)
        s.avail[j] = avail[j];
    computeneed(s);
}
 
void runsimulation(int simnum, const char *desc,
                   struct systemstate &s,
                   int requests[][maxr], int reqpids[], int nreqs) {
    cout << "\nSimulation " << simnum << ": " << desc << "\n";

    int seq[maxp], len;
    cout << "need matrix computed.\n";
    if (safetycheck(s, seq, len)) {
        cout << "initial ";
        printsequence(seq, len);
    } else {
        cout << "system starts in an unsafe state.\n";
    }
    printstate(s);

    for (int r = 0; r < nreqs; r++) {
        handlerequest(s, reqpids[r], requests[r]);
        printstate(s);
    }
}
 
void runallsimulations() {
    struct systemstate s;
    int maxd1[5][maxr]  = {{7,5,3},{3,2,2},{9,0,2},{2,2,2},{4,3,3}};
    int alloc1[5][maxr] = {{0,1,0},{2,0,0},{3,0,2},{2,1,1},{0,0,2}};
    int avail1[]        = {3,3,2};
    loadsim(s, 5, 3, maxd1, alloc1, avail1);
    int reqs1[3][maxr] = {{1,0,2},{3,3,0},{2,0,0}};
    int pids1[3]       = {1, 4, 0};
    runsimulation(1, "Classic Textbook Safe State (5P, 3R)", s, reqs1, pids1, 3);

    int maxd2[3][maxr]  = {{3,2,2},{2,2,2},{2,1,1}};
    int alloc2[3][maxr] = {{1,0,0},{0,1,0},{0,0,1}};
    int avail2[]        = {2,2,2};
    loadsim(s, 3, 3, maxd2, alloc2, avail2);
    int reqs2[3][maxr] = {{5,0,0},{1,0,0},{0,1,0}};
    int pids2[3]       = {0, 1, 2};
    runsimulation(2, "Request Exceeds Need — Denied (3P, 3R)", s, reqs2, pids2, 3);

    int maxd3[4][maxr]  = {{4,4},{3,3},{2,2},{1,1}};
    int alloc3[4][maxr] = {{2,2},{1,1},{1,0},{0,0}};
    int avail3[]        = {1,1};
    loadsim(s, 4, 2, maxd3, alloc3, avail3);
    int reqs3[3][maxr] = {{1,1},{1,0},{0,1}};
    int pids3[3]       = {0, 2, 3};
    runsimulation(3, "Grant Would Cause Unsafe State — Rollback (4P, 2R)", s, reqs3, pids3, 3);

    int maxd4[3][maxr]  = {{4,2},{3,3},{2,2}};
    int alloc4[3][maxr] = {{2,1},{1,1},{1,0}};
    int avail4[]        = {1,0};
    loadsim(s, 3, 2, maxd4, alloc4, avail4);
    int reqs4[3][maxr] = {{2,1},{1,0},{1,0}};
    int pids4[3]       = {0, 1, 2};
    runsimulation(4, "Insufficient Available Resources — Must Wait (3P, 2R)", s, reqs4, pids4, 3);

    int maxd5[4][maxr]  = {{3,3,2},{4,2,2},{2,2,2},{3,2,1}};
    int alloc5[4][maxr] = {{1,0,0},{0,1,0},{1,1,0},{0,0,1}};
    int avail5[]        = {2,2,2};
    loadsim(s, 4, 3, maxd5, alloc5, avail5);
    int reqs5[3][maxr] = {{1,0,0},{0,1,0},{0,0,1}};
    int pids5[3]       = {0, 1, 2};
    runsimulation(5, "Sequential Grants — Safe Sequence Evolves (4P, 3R)", s, reqs5, pids5, 3);
}
int main() {
    runallsimulations();
 
    struct systemstate s;
 
    loadinput(s);
    computeneed(s);
 
    int seq[maxp], len;
    cout << "\nneed matrix computed.\n";
    if (safetycheck(s, seq, len)) {
        cout << "initial ";
        printsequence(seq, len);
    } else {
        cout << "system starts in an unsafe state.\n";
    }
 
    printstate(s);
 
    int reqcount = 0;
    cout << "\nruntime requests (enter -1 to exit, at least 3 required):\n";
 
    while (1) {
        int pid;
        cout << "\nenter process id (-1 to exit): ";
        cin >> pid;
 
        if (pid == -1) {
            if (reqcount < 3) {
                cout << "warning: only " << reqcount << " request(s) made. need at least 3.\n";
                cout << "continue? (1=yes / 0=exit): ";
                int ch; cin >> ch;
                if (ch != 1) break;
                continue;
            }
            break;
        }
 
        if (pid < 0 || pid >= s.np) {
            cout << "invalid process id. range: 0 to " << s.np - 1 << ".\n";
            continue;
        }
 
        int req[maxr];
        cout << "enter request vector (" << s.nr << " values): ";
        int valid = 1;
        for (int count2 = 0; count2 < s.nr; count2++) {
            cin >> req[count2];
            if (req[count2] < 0) valid = 0;
        }
        if (!valid) { cout << "request values must be >= 0.\n"; continue; }
 
        handlerequest(s, pid, req);
        reqcount++;
        printstate(s);
    }
 
    cout << "\ntotal requests handled: " << reqcount << "\n";
    return 0;
}