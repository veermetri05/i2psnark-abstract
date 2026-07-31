import io

BASE = "C:/Users/VeerMetri/Documents/projects/i2psnark-abstract/src/main/java/org/klomp/snark/"

def load(name):
    return io.open(BASE + name, encoding="utf-8").read()

def save(name, s):
    io.open(BASE + name, "w", encoding="utf-8", newline="\n").write(s)

p = "PeerCoordinator.java"
s = load(p)

# field + setter
s = s.replace("""  private final List<PartialPiece> partialPieces;""",
              """  private final List<PartialPiece> partialPieces;

  /** per-torrent override of the ClientContext default; null = use default */
  private volatile ClientContext.PieceSelection _pieceSelection;""")

s = s.replace("""  public Storage getStorage() { return storage; }""",
              """  public Storage getStorage() { return storage; }

  /**
   *  Override the piece selection strategy for this torrent
   *  (WS-1.4). Null restores the ClientContext default.
   */
  public void setPieceSelection(ClientContext.PieceSelection selection) {
      _pieceSelection = selection;
  }

  /** @return the active selection strategy */
  private ClientContext.PieceSelection pieceSelection() {
      ClientContext.PieceSelection sel = _pieceSelection;
      if (sel == null)
          sel = _ctx.getPieceSelection();
      return sel != null ? sel : ClientContext.PieceSelection.RAREST_FIRST;
  }

  /**
   *  Order the wanted pieces per the active strategy (WS-1.4):
   *  rarest-first (Piece.compareTo), ascending id (sequential), or
   *  ends-first (first/last piece priority).
   */
  private void sortWantedPieces() {
      switch (pieceSelection()) {
          case SEQUENTIAL:
              Collections.sort(wantedPieces, (a, b) -> a.getId() - b.getId());
              break;
          case FIRST_LAST:
              Collections.sort(wantedPieces, (a, b) -> {
                  int da = endDistance(a.getId());
                  int db = endDistance(b.getId());
                  if (da != db)
                      return da - db;
                  return a.getId() - b.getId();
              });
              break;
          default:
              // Sort in order of rarest first (priority desc, then rarest)
              Collections.sort(wantedPieces);
      }
  }

  /** distance to the nearer end of the torrent, for FIRST_LAST */
  private int endDistance(int piece) {
      int total = metainfo != null ? metainfo.getPieces() : 0;
      if (total <= 0)
          return 0;
      return Math.min(piece, total - 1 - piece);
  }""")

# setWantedPieces: shuffle only for rarest-first
s = s.replace("""                  wantedPieces.add(p);
                  count += metainfo.getPieceLength(i);
              }
          }
          wantedBytes = count;
          Collections.shuffle(wantedPieces, _random);
      }
  }""",
              """                  wantedPieces.add(p);
                  count += metainfo.getPieceLength(i);
              }
          }
          wantedBytes = count;
          if (pieceSelection() == ClientContext.PieceSelection.RAREST_FIRST)
              Collections.shuffle(wantedPieces, _random);
          else
              sortWantedPieces();
      }
  }""")

# updatePiecePriorities: same shuffle policy
s = s.replace("""          // if we added pieces, they will be in-order unless we shuffle
          Collections.shuffle(wantedPieces, _random);""",
              """          // if we added pieces, they will be in-order unless we shuffle
          if (pieceSelection() == ClientContext.PieceSelection.RAREST_FIRST)
              Collections.shuffle(wantedPieces, _random);
          else
              sortWantedPieces();""")

# wantPiece: use the strategy sort
s = s.replace("""        if (record)
            Collections.sort(wantedPieces); // Sort in order of rarest first.""",
              """        if (record)
            sortWantedPieces();""")

save(p, s)
print("done")
